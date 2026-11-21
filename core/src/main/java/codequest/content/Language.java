package codequest.content;

import java.util.Locale;

/**
 * A programming language the game can teach. A class teaches one of them (classes.language) and everything that student
 * plays and reads follows it: the built-in lessons and questions, the dictionary, and what the AI writes. C++ is the pilot
 * language of the project, so it is the default for new classes; C stays available for the classes that already use it.
 */
public enum Language {
    C("C"),
    CPP("C++");

    /** The language new classes start with and the built-in lessons use when no class says otherwise. */
    public static final Language PILOT = CPP;

    private final String key;

    Language(String key) {
        this.key = key;
    }

    /** What is stored in the database (classes.language, challenges.language, dictionary_entries.language) and shown. */
    public String key() {
        return key;
    }

    /** The language for a stored key; a missing or unknown key is C, because every class made before C++ was added is C. */
    public static Language fromKey(String key) {
        if (key != null) {
            String k = key.trim().toUpperCase(Locale.ROOT);
            for (Language l : values()) {
                if (l.key.equals(k)) {
                    return l;
                }
            }
        }
        return C;
    }

    /** The name of the code a program of this language starts with, for hints: "stdio.h" or "iostream". */
    public String ioLibrary() {
        return this == CPP ? "iostream" : "stdio.h";
    }
}
