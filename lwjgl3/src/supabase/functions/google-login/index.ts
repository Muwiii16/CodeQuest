// google-login: sign in with the school Google account (@ub.edu.ph) instead of a username and password.
// The game opens Google in the browser (PKCE, loopback address). Google sends the browser back to the game with a one-time
// code; the game gives the code to this function, which swaps it for the signed-in person's identity DIRECTLY with Google
// (so the email and name cannot be faked by the game), checks the address, and hands the verified email to the database
// function app_google_login, which decides student / professor, finds or creates the profile and returns a session token.
// Secrets (set by the project owner in Supabase, Edge Functions > Secrets): GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const DOMAIN = "ub.edu.ph";
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-session",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });

// the game listens on the computer itself; nothing else may receive the code
const LOOPBACK = /^http:\/\/127\.0\.0\.1:\d{2,5}\/?$/;

function decodeIdToken(idToken: string): Record<string, unknown> | null {
  try {
    const part = idToken.split(".")[1];
    const b64 = part.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(part.length / 4) * 4, "=");
    return JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(b64), (c) => c.charCodeAt(0))));
  } catch {
    return null;
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "POST only" }, 405);

  const clientId = Deno.env.get("GOOGLE_CLIENT_ID");
  const clientSecret = Deno.env.get("GOOGLE_CLIENT_SECRET");
  if (!clientId || !clientSecret) {
    return json({ error: "Google sign-in is not set up yet: the GOOGLE_CLIENT_ID or GOOGLE_CLIENT_SECRET secret is missing." }, 503);
  }

  let body: Record<string, string>;
  try {
    body = await req.json();
  } catch {
    return json({ error: "Send a JSON body." }, 400);
  }
  const redirectUri = String(body.redirect_uri ?? "");
  if (!LOOPBACK.test(redirectUri)) return json({ error: "Bad redirect address." }, 400);

  // step 1: the address the game opens in the browser
  if (body.action === "url") {
    const challenge = String(body.code_challenge ?? "");
    const state = String(body.state ?? "");
    if (!/^[A-Za-z0-9_-]{43}$/.test(challenge) || !/^[A-Za-z0-9_-]{16,128}$/.test(state)) {
      return json({ error: "Bad request." }, 400);
    }
    const params = new URLSearchParams({
      client_id: clientId,
      redirect_uri: redirectUri,
      response_type: "code",
      scope: "openid email profile",
      code_challenge: challenge,
      code_challenge_method: "S256",
      state,
      hd: DOMAIN, // only a hint for the account picker; the check below is what counts
      prompt: "select_account",
    });
    return json({ url: "https://accounts.google.com/o/oauth2/v2/auth?" + params.toString() });
  }

  // step 2: the one-time code -> who signed in
  if (body.action !== "exchange") return json({ error: "Unknown action." }, 400);
  const code = String(body.code ?? "");
  const verifier = String(body.code_verifier ?? "");
  if (!code || code.length > 2048 || !/^[A-Za-z0-9_.~-]{43,128}$/.test(verifier)) return json({ error: "Bad request." }, 400);

  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      code,
      client_id: clientId,
      client_secret: clientSecret,
      redirect_uri: redirectUri,
      grant_type: "authorization_code",
      code_verifier: verifier,
    }),
  });
  if (!tokenRes.ok) return json({ error: "Google did not accept the sign-in. Try again." }, 401);
  const tokens = await tokenRes.json();
  const claims = typeof tokens.id_token === "string" ? decodeIdToken(tokens.id_token) : null;
  if (!claims) return json({ error: "Google did not say who you are. Try again." }, 401);

  // the token came straight from Google over TLS; these checks make sure it is for this app and for a school account
  const email = String(claims.email ?? "").toLowerCase();
  const issuer = String(claims.iss ?? "");
  const now = Math.floor(Date.now() / 1000);
  if (claims.aud !== clientId || (issuer !== "https://accounts.google.com" && issuer !== "accounts.google.com") ||
      Number(claims.exp) < now) {
    return json({ error: "Google sign-in could not be verified." }, 401);
  }
  if (claims.email_verified !== true || !email.endsWith("@" + DOMAIN) || claims.hd !== DOMAIN) {
    return json({ error: "Please sign in with your school account (name@" + DOMAIN + ")." }, 403);
  }

  const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data, error } = await sb.rpc("app_google_login", { p_email: email, p_name: String(claims.name ?? "") });
  if (error || !data) return json({ error: "Could not sign you in right now. Try again." }, 500);
  if (data.error === "not_allowed") {
    return json({
      error: "This school address is not recognised. Student addresses are numbers (2102122@" + DOMAIN +
        "), professor addresses are names.",
    }, 403);
  }
  if (data.error) return json({ error: "Could not sign you in right now. Try again." }, 500);
  return json(data);
});
