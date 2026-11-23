/**
 * CodeQuest UAT questionnaire builder (Google Apps Script).
 *
 * How to use:
 *   1. Go to https://script.google.com, click "New project".
 *   2. Delete the sample code, paste this whole file, click Save.
 *   3. Choose the function "createUatForm" and click Run. Allow the permissions it asks for
 *      (it only creates a Google Form in your own Drive).
 *   4. Open the View > Logs panel: it prints the link students fill in and the link you edit.
 *
 * Nothing here is sent anywhere else; the form belongs to the account that runs the script.
 */
function createUatForm() {
  var form = FormApp.create("CodeQuest - User Acceptance Testing (1st Year Students)");
  form.setDescription(
    "CodeQuest is a game that teaches introductory programming. Please play it for at least " +
    "20 minutes, then answer this form honestly. There are no right or wrong answers: we are " +
    "testing the game, not you. It takes about 10 minutes.\n\n" +
    "Team: The Wolves, UB CICT.");
  form.setCollectEmail(false);
  form.setProgressBar(true);
  form.setShowLinkToRespondAgain(false);
  form.setConfirmationMessage("Thank you! Your answers help us improve CodeQuest.");

  var AGREE = ["Strongly disagree", "Disagree", "Neutral", "Agree", "Strongly agree"];

  // ---------- Section 1: consent ----------
  form.addSectionHeaderItem().setTitle("1. Consent")
    .setHelpText("Your answers are used only for our thesis. We collect no passwords. Results are reported " +
      "as groups, not as individuals. You may stop at any time.");
  form.addMultipleChoiceItem()
    .setTitle("I understand this and agree to take part.")
    .setChoiceValues(["Yes, I agree", "No"])
    .setRequired(true);

  // ---------- Section 2: about you ----------
  form.addPageBreakItem().setTitle("2. About you");
  form.addTextItem()
    .setTitle("Your tester number")
    .setHelpText("Example: zz_uat_student07. Use only the tester number your facilitator gave you, never your real name " +
      "or password. It only lets us match your answers to what we saw in the game.")
    .setRequired(true);
  form.addMultipleChoiceItem()
    .setTitle("Your course")
    .setChoiceValues(["BSCS", "BSIT", "BSIS", "Other"])
    .setRequired(true);
  form.addMultipleChoiceItem()
    .setTitle("Before this semester, how much programming had you done?")
    .setChoiceValues(["None at all", "A little (a tutorial or a school activity)", "Some (small programs)", "A lot"])
    .setRequired(true);
  form.addMultipleChoiceItem()
    .setTitle("How often do you play video games?")
    .setChoiceValues(["Never", "A few times a month", "A few times a week", "Almost daily"])
    .setRequired(true);
  form.addTextItem()
    .setTitle("What computer did you use? (optional)")
    .setHelpText("Example: school laptop, 8 GB RAM, Windows 11. Helps us find performance problems.");

  // ---------- Section 3: tasks ----------
  form.addPageBreakItem().setTitle("3. Tasks")
    .setHelpText("For each thing below, tell us how it went. Choose \"Did not try\" if you did not get to it.");
  form.addGridItem()
    .setTitle("How did each task go?")
    .setRows([
      "Install and open the game",
      "Create an account and log in",
      "Join a class using a class code",
      "Start a lesson and play the first stage",
      "Answer the questions during a stage",
      "Read the explanation after an answer",
      "Check my score and progress",
      "Open my badges or certificates",
      "Roll or equip a reward / cosmetic",
      "Leave the game (Esc key)"
    ])
    .setColumns(["Easy", "Needed some effort", "Could not do it", "Did not try"])
    .setRequired(true);

  // ---------- Section 4: usability (SUS) ----------
  form.addPageBreakItem().setTitle("4. Ease of use")
    .setHelpText("Choose how much you agree with each statement.");
  form.addGridItem()
    .setTitle("System Usability Scale")
    .setRows([
      "I think I would like to play CodeQuest often.",
      "I found the game more complicated than it needs to be.",
      "I thought the game was easy to use.",
      "I think I would need help from someone to be able to use the game.",
      "The different parts of the game fit together well.",
      "There was too much inconsistency in the game.",
      "I think most students would learn to use this game quickly.",
      "I found the game awkward to use.",
      "I felt confident using the game.",
      "I needed to learn a lot before I could get going."
    ])
    .setColumns(AGREE)
    .setRequired(true);

  // ---------- Section 5: learning and gameplay ----------
  form.addPageBreakItem().setTitle("5. Learning and gameplay")
    .setHelpText("Choose how much you agree with each statement.");
  form.addGridItem()
    .setTitle("About the game")
    .setRows([
      "The instructions told me what to do.",
      "The questions matched what I am learning in class.",
      "The questions were not too hard or too easy.",
      "The timer felt fair.",
      "The text was big and clear enough to read.",
      "The explanation after each answer helped me understand.",
      "The rewards (badges, items, certificates) made me want to keep playing.",
      "I learned something about programming by playing.",
      "I would recommend CodeQuest to a classmate."
    ])
    .setColumns(AGREE)
    .setRequired(true);
  form.addMultipleChoiceItem()
    .setTitle("The screen effects during questions (zoom, shake, speed lines) were:")
    .setChoiceValues(["Fun and made it exciting", "Fine, I did not mind them", "Distracting", "Made me dizzy or uncomfortable"])
    .setRequired(true);
  form.addScaleItem()
    .setTitle("Overall, how would you rate CodeQuest?")
    .setBounds(1, 5)
    .setLabels("Poor", "Excellent")
    .setRequired(true);

  // ---------- Section 6: problems ----------
  form.addPageBreakItem().setTitle("6. Problems");
  form.addMultipleChoiceItem()
    .setTitle("Did the game run smoothly?")
    .setChoiceValues(["Yes, no lag", "Sometimes laggy", "Often laggy or slow"])
    .setRequired(true);
  form.addMultipleChoiceItem()
    .setTitle("Did the game crash, freeze, or close by itself?")
    .setChoiceValues(["No", "Once", "More than once"])
    .setRequired(true);
  form.addParagraphTextItem()
    .setTitle("Describe any bug or error you saw")
    .setHelpText("Which screen, what you did just before, and what went wrong. A screenshot link helps (Win + Shift + S).");
  form.addParagraphTextItem()
    .setTitle("Was anything confusing or hard to read?");

  // ---------- Section 7: open feedback ----------
  form.addPageBreakItem().setTitle("7. Your thoughts");
  form.addParagraphTextItem().setTitle("What did you like most about CodeQuest?");
  form.addParagraphTextItem().setTitle("What should we improve first?");
  form.addParagraphTextItem().setTitle("Anything else you want to tell us? (optional)");

  Logger.log("Students fill in: " + form.getPublishedUrl());
  Logger.log("You edit here:    " + form.getEditUrl());
}
