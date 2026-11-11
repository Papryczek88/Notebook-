
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

int main(int argc, char *argv[]) {
    if (argc < 2 || strcmp(argv[1], "count") != 0) {
        fprintf(stderr, "Notebook+ Text Engine\n");
        fprintf(stderr, "Usage: notebook_engine.exe count\n");
        return 1;
    }

    size_t capacity = 4096;
    size_t length = 0;

    char *text = malloc(capacity);

    if (text == NULL) {
        fprintf(stderr, "Memory allocation error.\n");
        return 2;
    }

    int ch;

    while ((ch = getchar()) != EOF) {
        if (length + 1 >= capacity) {
            if (capacity > (size_t)-1 / 2) {
                free(text);
                fprintf(stderr, "Text is too large.\n");
                return 2;
            }

            size_t new_capacity = capacity * 2;
            char *new_text = realloc(text, new_capacity);

            if (new_text == NULL) {
                free(text);
                fprintf(stderr, "Memory allocation error.\n");
                return 2;
            }

            text = new_text;
            capacity = new_capacity;
        }

        text[length++] = (char)ch;
    }

    size_t words = 0;
    size_t lines = 0;
    int inside_word = 0;

    for (size_t i = 0; i < length; i++) {
        unsigned char c = (unsigned char)text[i];

        if (isspace(c)) {
            inside_word = 0;
        } else if (!inside_word) {
            words++;
            inside_word = 1;
        }

        if (c == '\n') {
            lines++;
        }
    }

    if (length > 0 && text[length - 1] != '\n') {
        lines++;
    }

    printf("NOTEBOOK+ PROFESSIONAL\n");
    printf("==============================\n");
    printf("Text size (UTF-8 bytes): %zu\n", length);
    printf("Words: %zu\n", words);
    printf("Lines: %zu\n", lines);
    printf("Allocated text memory: %zu bytes\n", length + 1);
    printf("==============================\n");
    printf("Analysis completed successfully.\n");

    free(text);
    return 0;
}
