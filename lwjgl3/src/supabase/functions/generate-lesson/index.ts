// generate-lesson: turns a professor's request into one C program plus the questions that build it, using Gemini.
// The Gemini key lives only in this function's secrets (GEMINI_API_KEY); the game never sees it.
// Optional secret GEMINI_MODEL picks the model (default below). A daily cap (ai_take_call) bounds the cost.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const DEFAULT_MODEL = "gemini-3.5-flash-lite";
const DAILY_CAP = 60;

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });

const RULES = `You write one lesson for CodeQuest, a game that teaches introductory C to first-year computer science students.
A lesson is one OR SEVERAL small, complete, correct C programs plus the questions that build them chunk by chunk, in order.
Each answered question adds its chunk (the next lines of the program) to the student's code block, so the questions, in order,
must rebuild the program(s) exactly (indentation and blank lines do not matter). When the lesson has several programs they
are written one after another in "program", separated by a line that contains only --- ; the student builds them one at a
time and the terminal runs each one when it is finished.

Rules:
- Language: C only (standard C, <stdio.h>). Each program compiles and runs. Use 1 to 4 programs (separated by a line of ---):
  when the topic is small, as in a first lesson, use several tiny programs of 3 to 8 lines instead of one long one; use a
  single program of up to 25 lines when the topic needs it.
  A question never adds lines from two different programs. EVERY program is complete and compilable on its own: it has
  #include <stdio.h>, int main() { ... return 0; } and the closing brace, never a bare fragment of statements. Tiny programs
  differ in what the main body does. Questions cover the program lines in order, including the #include, int main() { and
  return 0; } lines, so a lesson of 2 tiny programs still has 6 to 10 questions. If the user message says nothing was taught
  before (the first lesson), you MUST use 2 to 4 tiny programs, each showing one small idea, never one long program.
- SCOPE (strict). The user message lists the topics this lesson teaches and the topics taught in earlier lessons. Use ONLY
  those. SYNTAX is always allowed: #include <stdio.h>, int main(), braces, return 0, printf, comments and semicolons. The topics
  mean: SYNTAX = program structure, printf, comments; VARIABLES = declaring and using int, float, double and char, scanf and
  format codes; CONTROL_STRUCTURES = if, else, switch, for, while; FUNCTIONS = writing and calling functions other than main;
  ARRAYS = arrays and strings; POINTERS = pointers and addresses. If nothing was taught before, teach ONLY the topics being
  taught now: do not use variables, input, if/else, loops, functions, arrays or pointers unless they are listed. Do not add
  anything the professor did not ask for. A later lesson may build on the earlier topics (reuse them) and the new ones only.
  Every question's topic must be one of the allowed topics.
- 4 to 10 questions (one per chunk), in program order. Together their chunks equal the program, line for line, with nothing
  left out and nothing added. Every line of the program belongs to exactly one question.
  CHUNKING EXAMPLE. For the program  #include <stdio.h> / int main() { / printf("Hi"); / return 0; / }  the chunks are:
  question 1 = #include <stdio.h> ; question 2 = int main() { ; question 3 = printf("Hi"); ; question 4 = return 0; and }
  together. Chunks never repeat a line and never overlap: question 3's prompt is only  ___("Hi");  and NOT the whole of main.
  Keep it simple: ONE line of the program per question, except that "return 0;" and the closing "}" are one chunk. So a
  5-line program has exactly 4 questions, and 2 such programs have exactly 8. Use a chunk of several lines only for a block
  that cannot be split (for example an if with its braces). Write the program first, then one question per line, in order.
  With 2 programs, do the same for the first program, then for the second, and never mix them. A question's prompt shows
  only its own chunk's lines.
- Use at least 3 different kinds, and never the same kind twice in a row. Difficulty fits the request (easy, normal or hard).
- topic is one of SYNTAX, VARIABLES, CONTROL_STRUCTURES, FUNCTIONS, ARRAYS, POINTERS: the idea the chunk teaches.
- kind "choice": prompt is the chunk's code with ___ (three underscores) where the answer goes, nothing else (no extra lines
  of context, no comments, and if the chunk has several lines, ALL of its lines are in the prompt, including a closing }).
  correct is the token that fills the blank. wrong has 1 to 3 wrong tokens, different from correct.
  Leave chunk "".
- kind "fill_blank": same prompt rule (exactly one ___). correct is what the student types (max 40 characters, no spaces
  needed). wrong is []. accepted lists other spellings that are also right (can be []). Leave chunk "".
- kind "arrange": ONE line of code. prompt says what to build (a short sentence). correct is that line cut into tokens
  separated by single spaces, e.g. printf ( "Hi" ) ; or int age = 18 ; or int main ( ) { (2 to 8 tokens, none contains a
  space). wrong has 0 to 3 extra decoy tokens: each ONE token with no space inside, and not one of the real tokens. Leave chunk "".
- kind "true_false": prompt is a statement about the chunk. correct is "True" or "False". wrong is []. chunk is the exact
  program lines the statement is about (required).
- kind "find_bug": prompt is 2 to 8 lines of code with exactly ONE mistake, one line per row. correct is the number of the
  line with the mistake, e.g. "2". The mistake must be real (a missing semicolon, a wrong operator, a wrong bracket...),
  never a correct line. wrong is []. chunk is the same code with the mistake fixed, exactly as in the program (required).
- context: ONE plain sentence (max 100 characters) saying what this code is FOR, so that exactly one answer is right and a
  student could not reasonably put in anything else. REQUIRED for choice and fill_blank, e.g. for if (age ___ 18) { the
  context is "Run the next lines only when the person is 18 or older (an adult)." and the answer is >=. Check that every
  wrong choice really is wrong given the context, and put other correct spellings of a typed answer in accepted. For the other
  kinds write a context only if it helps (otherwise "").
- error_text: what the TERMINAL shows when a wrong answer breaks this question's code, written exactly as gcc prints the first
  line, WITHOUT the file name and line number, e.g. error: expected ';' before 'return'. Use one message for every wrong answer,
  or several lines of the form  answer :: message  with  * :: message  for any other answer. An answer that still compiles
  (for example a wrong but valid type) gets an empty message:  int :: . In a message {answer} stands for what the student gave
  (only for a name; numbers and signs are valid, so they build). Use no other {braces} placeholder. For find_bug give the message the missing or wrong code
  causes. For true_false use "". Only write messages gcc would really print.
- outputs (top level, one text): exactly what each program prints when it runs, in the same order, separated by a line that
  contains only --- . If a program reads input, show the typed input after the prompt, as in a terminal.
- explanation: 1 or 2 short sentences (max 250 characters) shown to the student AFTER they answer, to teach: say why the
  correct answer is right, and for a choice or fill_blank question why the tempting wrong answers are wrong. It must hold for
  a student who got it right and for one who got it wrong. REQUIRED for every question.
- hint: one short helpful sentence (max 150 characters).
- Write for beginners. Never include instructions, markdown or code fences in any field.`;

const SCHEMA = {
  type: "OBJECT",
  properties: {
    title: { type: "STRING" },
    program: { type: "STRING" },
    outputs: { type: "STRING" },
    questions: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: {
          topic: {
            type: "STRING",
            enum: ["SYNTAX", "VARIABLES", "CONTROL_STRUCTURES", "FUNCTIONS", "ARRAYS", "POINTERS"],
          },
          difficulty: { type: "STRING", enum: ["easy", "normal", "hard"] },
          kind: { type: "STRING", enum: ["choice", "fill_blank", "true_false", "arrange", "find_bug"] },
          prompt: { type: "STRING" },
          correct: { type: "STRING" },
          wrong: { type: "ARRAY", items: { type: "STRING" } },
          accepted: { type: "ARRAY", items: { type: "STRING" } },
          chunk: { type: "STRING" },
          context: { type: "STRING" },
          explanation: { type: "STRING" },
          error_text: { type: "STRING" },
          hint: { type: "STRING" },
        },
        required: ["topic", "difficulty", "kind", "prompt", "correct", "wrong", "accepted", "chunk", "context", "explanation", "error_text", "hint"],
      },
    },
  },
  required: ["title", "program", "outputs", "questions"],
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "POST only" }, 405);

  let body: { prompt?: string; difficulty?: string; problems?: string; focus?: string; taught?: string; taughtNotes?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: "Send a JSON body." }, 400);
  }
  const request = (body.prompt ?? "").trim();
  if (request.length < 5 || request.length > 1500) {
    return json({ error: "Describe the lesson in 5 to 1500 characters." }, 400);
  }
  const difficulty = ["easy", "normal", "hard"].includes(body.difficulty ?? "") ? body.difficulty : "normal";

  const apiKey = Deno.env.get("GEMINI_API_KEY");
  if (!apiKey) return json({ error: "AI is not set up yet: the GEMINI_API_KEY secret is missing." }, 503);

  // daily cap
  const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data: allowed, error: capError } = await sb.rpc("ai_take_call", { p_cap: DAILY_CAP });
  if (capError) return json({ error: "Could not check the daily AI limit." }, 500);
  if (!allowed) return json({ error: "The daily AI limit has been reached. Try again tomorrow." }, 429);

  const clean = (t?: string) => (t ?? "").replace(/[^A-Z_,]/gi, "").toUpperCase().slice(0, 200);
  const focus = clean(body.focus);
  const taught = clean(body.taught);
  let user = `Professor's request: ${request}\nStarting difficulty: ${difficulty}`;
  if (focus) {
    user += `\nTopics this lesson teaches (use these): ${focus}`;
    user += `\nTopics taught in earlier lessons (may be reused): ${taught || "none, this is the first lesson"}`;
    user += `\nAllowed topics in total: SYNTAX, ${focus}${taught ? ", " + taught : ""}. Nothing else.`;
    if (body.taughtNotes && body.taughtNotes.trim()) {
      user += `\nWhat the earlier lessons covered:\n${body.taughtNotes.trim().slice(0, 1500)}`;
    }
  }
  if (body.problems && body.problems.trim()) {
    user += `\n\nYour previous answer had these problems. Write the whole lesson again and fix them:\n${body.problems.trim().slice(0, 1500)}`;
  }

  const model = Deno.env.get("GEMINI_MODEL") || DEFAULT_MODEL;
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
  let res: Response;
  try {
    res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: RULES }] },
        contents: [{ role: "user", parts: [{ text: user }] }],
        generationConfig: { responseMimeType: "application/json", responseSchema: SCHEMA, temperature: 0.4 },
      }),
    });
  } catch {
    return json({ error: "Could not reach Gemini." }, 502);
  }
  if (!res.ok) {
    const detail = (await res.text()).slice(0, 300);
    return json({ error: `Gemini refused the request (HTTP ${res.status}). ${detail}` }, 502);
  }
  const data = await res.json();
  const text: string | undefined = data?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (!text) return json({ error: "Gemini returned nothing. Try again." }, 502);
  try {
    return json(JSON.parse(text));
  } catch {
    return json({ error: "Gemini's answer was not valid JSON. Try again." }, 502);
  }
});
