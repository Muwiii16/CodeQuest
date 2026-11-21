package codequest.screens;

import codequest.Main;
import codequest.content.CTopic;
import codequest.data.CampaignData;
import codequest.data.DatabaseManager;
import codequest.data.DictionaryData;
import codequest.data.DictionaryData.Entry;
import codequest.screens.PixelFonts.Face;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;

import java.util.List;

/**
 * The dictionary: every term with a plain meaning and an example. Search by typing, or narrow it to a topic; click a term
 * to read it. The entries are the same for every class that teaches the language. It is opened from a class in the
 * campaign and shows only the language that class teaches.
 */
public class DictionaryScreen extends StudentScreen {

    private static final int ROWS = 10;

    private final DictionaryData data = new DictionaryData();
    private final TextField searchField;
    private final Group languageRegion;
    private final Group listRegion;
    private final Group detailRegion;
    private final Group chipRegion;
    private List<String> languages = List.of();
    private String languageFilter; // null = every language shown
    private List<Entry> all = List.of();
    private List<Entry> shown = List.of();
    private CTopic topic;
    private Entry selected;
    private int page;

    /** From a class in the campaign: only the language that class teaches. */
    public DictionaryScreen(Main game, String username, String language, Runnable back) {
        super(game, username, language == null ? "Dictionary" : language + " dictionary", back);
        chipRegion = region();
        searchField = textField("Search a word or an idea", false, 60, 200, 700, 56, Face.SANS);
        searchField.setTextFieldListener((field, c) -> {
            page = 0;
            refilter();
        });
        languageRegion = region();
        listRegion = region();
        card(800, 205, 1060, 805);
        detailRegion = region();
        rebuildChips();
        load("Loading the dictionary...", () -> {
            List<String> langs = language != null ? List.of(language)
                    : DictionaryData.languagesOf(new CampaignData().studentClasses(new DatabaseManager().profileId(username)));
            return new Loaded(langs, data.entries(langs));
        }, loaded -> {
            languages = loaded.languages();
            all = loaded.entries();
            if (languages.size() > 1) {
                searchField.setWidth(480);
            }
            refilter();
        });
    }

    private record Loaded(List<String> languages, List<Entry> entries) {
    }

    /** Several languages: a button that steps through them, and back to all of them together. */
    private void rebuildLanguageButton() {
        fill(languageRegion, () -> {
            if (languages.size() > 1) {
                solidButton("Language: " + (languageFilter == null ? "All" : languageFilter), 580, 200, 180, 56, BUTTON, () -> {
                    int i = languageFilter == null ? -1 : languages.indexOf(languageFilter);
                    languageFilter = i + 1 >= languages.size() ? null : languages.get(i + 1);
                    page = 0;
                    refilter();
                });
            }
        });
    }

    private void rebuildChips() {
        fill(chipRegion, () -> {
            float x = 60;
            solidButton("All", x, 130, 90, 54, topic == null ? GOOD : BUTTON, () -> pick(null));
            x += 100;
            for (CTopic t : CTopic.values()) {
                String label = ProfessorLessonScreen.CosmeticTopic.label(t);
                float w = 40 + label.length() * 13;
                solidButton(label, x, 130, w, 54, t == topic ? GOOD : BUTTON, () -> pick(t));
                x += w + 8;
            }
        });
    }

    private void pick(CTopic t) {
        topic = t;
        page = 0;
        rebuildChips();
        refilter();
    }

    private void refilter() {
        shown = DictionaryData.filter(all, languageFilter, topic, searchField.getText());
        rebuildLanguageButton();
        if (selected == null || !shown.contains(selected)) {
            selected = shown.isEmpty() ? null : shown.get(0);
        }
        rebuildList();
        rebuildDetail();
    }

    private void rebuildList() {
        fill(listRegion, () -> {
            if (all.isEmpty()) {
                text("There are no dictionary entries for " + String.join(", ", languages)
                        + " yet, or the connection failed.", 60, 290, 700, 80, 1.5f, MUTED,
                        Align.topLeft, Face.SANS).setWrap(true);
                return;
            }
            if (shown.isEmpty()) {
                text("Nothing matches that. Try another word.", 60, 290, 700, 60, 1.5f, MUTED, Align.topLeft, Face.SANS);
                return;
            }
            int from = page * ROWS;
            int to = Math.min(shown.size(), from + ROWS);
            float y = 275;
            for (int i = from; i < to; i++) {
                Entry e = shown.get(i);
                solidButton(e.term(), 60, y, 700, 56, e == selected ? GOOD : ROW, Color.WHITE, Face.MONO, () -> {
                    selected = e;
                    rebuildList();
                    rebuildDetail();
                });
                y += 64;
            }
            if (shown.size() > ROWS) {
                int pages = (shown.size() + ROWS - 1) / ROWS;
                if (page > 0) {
                    solidButton("< Prev", 60, 940, 200, 56, BUTTON, () -> {
                        page--;
                        rebuildList();
                    });
                }
                text("Page " + (page + 1) + " / " + pages, 280, 940, 280, 56, 1.4f, MUTED, Align.center, Face.SANS);
                if (page < pages - 1) {
                    solidButton("Next >", 560, 940, 200, 56, BUTTON, () -> {
                        page++;
                        rebuildList();
                    });
                }
            }
        });
    }

    private void rebuildDetail() {
        fill(detailRegion, () -> {
            if (selected == null) {
                return;
            }
            text(selected.term(), 840, 225, 980, 70, 3.2f, Color.WHITE, Align.left, Face.MONO);
            text((languages.size() > 1 ? selected.language() + "   -   " : "") + ProfessorLessonScreen.CosmeticTopic.label(selected.topic()),
                    840, 305, 700, 30, 1.3f, TEAL, Align.left,
                    Face.SANS);
            text(selected.meaning(), 840, 355, 980, 170, 1.9f, Color.WHITE, Align.topLeft, Face.SANS).setWrap(true);
            if (selected.example() != null && !selected.example().isBlank()) {
                text("Example", 840, 535, 400, 30, 1.3f, MUTED, Align.left, Face.SANS);
                rect(840, 575, 980, 390, new Color(0.02f, 0.03f, 0.09f, 1f));
                text(selected.example(), 870, 595, 920, 350, 2.0f, new Color(0.75f, 0.95f, 0.75f, 1f), Align.topLeft, Face.MONO);
            }
        });
    }
}
