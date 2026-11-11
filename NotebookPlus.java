
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.undo.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Function;

public class NotebookPlus extends JFrame {
    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel status = new JLabel(" Gotowy");
    private boolean dark = true;

    private static class Doc {
        final JTextArea area = new JTextArea();
        final JTextArea numbers = new JTextArea();
        final JScrollPane scroll;
        final UndoManager undo = new UndoManager();
        File file;
        boolean changed = false;
        boolean showNumbers = true;

        Doc() {
            area.setFont(new Font("Consolas", Font.PLAIN, 15));
            area.setTabSize(4);
            area.setLineWrap(false);
            area.setWrapStyleWord(true);
            area.setMargin(new Insets(8, 8, 8, 8));

            numbers.setEditable(false);
            numbers.setFocusable(false);
            numbers.setFont(new Font("Consolas", Font.PLAIN, 14));
            numbers.setBackground(new Color(38, 40, 46));
            numbers.setForeground(new Color(130, 145, 165));
            numbers.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            scroll = new JScrollPane(area);
            scroll.setRowHeaderView(numbers);

            area.getDocument().addUndoableEditListener(
                e -> undo.addEdit(e.getEdit())
            );

            area.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { update(); }
                public void removeUpdate(DocumentEvent e) { update(); }
                public void changedUpdate(DocumentEvent e) { update(); }

                private void update() {
                    changed = true;
                    refreshNumbers();
                }
            });

            area.addCaretListener(e -> refreshNumbers());
            refreshNumbers();
        }

        void refreshNumbers() {
            int lines = Math.max(1, area.getLineCount());
            StringBuilder result = new StringBuilder();

            for (int i = 1; i <= lines; i++) {
                result.append(i).append('\n');
            }

            numbers.setText(result.toString());
        }
    }

    public NotebookPlus() {
        super("Notebook+ Professional");

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1100, 720);
        setMinimumSize(new Dimension(750, 480));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(createToolbar(), BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        setJMenuBar(createMenu());

        tabs.addChangeListener(e -> updateStatus());
        addNewTab();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmAllSaved()) {
                    dispose();
                }
            }
        });

        applyTheme();
    }

    private JPanel createToolbar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        panel.setBackground(new Color(45, 48, 56));

        addButton(panel, "Nowy", this::addNewTab);
        addButton(panel, "Otwórz", this::openFile);
        addButton(panel, "Zapisz", this::saveFile);
        addButton(panel, "Zapisz jako", this::saveAs);
        addButton(panel, "Szukaj", this::findText);
        addButton(panel, "Statystyki", this::showStats);

        return panel;
    }

    private void addButton(JPanel panel, String title, Runnable action) {
        JButton button = new JButton(title);
        button.setFocusPainted(false);
        button.addActionListener(e -> action.run());
        panel.add(button);
    }

    private JMenuBar createMenu() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("Plik");
        addMenu(file, "Nowy", this::addNewTab);
        addMenu(file, "Otwórz...", this::openFile);
        addMenu(file, "Zapisz", this::saveFile);
        addMenu(file, "Zapisz jako...", this::saveAs);
        addMenu(file, "Zapisz wszystko", this::saveAll);
        addMenu(file, "Zamknij kartę", this::closeTab);
        file.addSeparator();
        addMenu(file, "Wyjście", () -> {
            if (confirmAllSaved()) dispose();
        });

        JMenu edit = new JMenu("Edycja");
        addMenu(edit, "Cofnij", this::undo);
        addMenu(edit, "Ponów", this::redo);
        addMenu(edit, "Szukaj", this::findText);
        addMenu(edit, "Zamień", this::replaceText);
        addMenu(edit, "Zaznacz wszystko", () -> editor().selectAll());
        addMenu(edit, "Wielkie litery", () -> transform(String::toUpperCase));
        addMenu(edit, "Małe litery", () -> transform(String::toLowerCase));
        addMenu(edit, "Usuń puste linie", this::removeEmptyLines);

        JMenu tools = new JMenu("Narzędzia");
        addMenu(tools, "Statystyki tekstu (C)", this::showStats);
        addMenu(tools, "Informacje o dokumencie", this::fileInfo);
        addMenu(tools, "Wyczyść edytor", this::clearEditor);

        JMenu view = new JMenu("Widok");
        addMenu(view, "Motyw ciemny/jasny", this::toggleTheme);
        addMenu(view, "Większa czcionka", () -> changeFont(1));
        addMenu(view, "Mniejsza czcionka", () -> changeFont(-1));
        addMenu(view, "Zawijanie tekstu", this::toggleWrap);
        addMenu(view, "Numerowanie linii", this::toggleLineNumbers);

        bar.add(file);
        bar.add(edit);
        bar.add(tools);
        bar.add(view);

        return bar;
    }

    private void addMenu(JMenu menu, String title, Runnable action) {
        JMenuItem item = new JMenuItem(title);
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    private void addNewTab() {
        Doc doc = new Doc();
        doc.scroll.putClientProperty("document", doc);

        tabs.addTab("Nowy dokument", doc.scroll);
        tabs.setSelectedComponent(doc.scroll);
        updateStatus();
        applyThemeTo(doc);
    }

    private Doc selectedDoc() {
        Component selected = tabs.getSelectedComponent();

        if (!(selected instanceof JScrollPane)) {
            return null;
        }

        return (Doc) ((JScrollPane) selected)
                .getClientProperty("document");
    }

    private JTextArea editor() {
        Doc doc = selectedDoc();
        return doc == null ? new JTextArea() : doc.area;
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser();

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();

        try {
            String content = Files.readString(
                file.toPath(), StandardCharsets.UTF_8
            );

            addNewTab();
            Doc doc = selectedDoc();

            doc.area.setText(content);
            doc.file = file;
            doc.changed = false;
            doc.undo.discardAllEdits();

            setTabName(doc, file.getName());
            status.setText(" Otwarto: " + file.getName());
            updateStatus();

        } catch (IOException ex) {
            showError("Nie można otworzyć pliku:\n" + ex.getMessage());
        }
    }

    private boolean saveFile() {
        Doc doc = selectedDoc();

        if (doc == null) return false;

        if (doc.file == null) {
            return saveAs();
        }

        try {
            Files.writeString(
                doc.file.toPath(),
                doc.area.getText(),
                StandardCharsets.UTF_8
            );

            doc.changed = false;
            setTabName(doc, doc.file.getName());
            status.setText(" Zapisano: " + doc.file.getName());
            return true;

        } catch (IOException ex) {
            showError("Błąd zapisu:\n" + ex.getMessage());
            return false;
        }
    }

    private boolean saveAs() {
        Doc doc = selectedDoc();

        if (doc == null) return false;

        JFileChooser chooser = new JFileChooser();

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return false;
        }

        doc.file = chooser.getSelectedFile();
        return saveFile();
    }

    private void saveAll() {
        int previous = tabs.getSelectedIndex();

        for (int i = 0; i < tabs.getTabCount(); i++) {
            tabs.setSelectedIndex(i);
            Doc doc = selectedDoc();

            if (doc != null && doc.changed) {
                if (!saveFile()) {
                    if (previous >= 0) tabs.setSelectedIndex(previous);
                    return;
                }
            }
        }

        if (previous >= 0 && previous < tabs.getTabCount()) {
            tabs.setSelectedIndex(previous);
        }

        status.setText(" Zapisano wszystkie dokumenty");
    }

    private boolean confirmAllSaved() {
        int previous = tabs.getSelectedIndex();

        for (int i = 0; i < tabs.getTabCount(); i++) {
            tabs.setSelectedIndex(i);
            Doc doc = selectedDoc();

            if (doc == null || !doc.changed) continue;

            int answer = JOptionPane.showConfirmDialog(
                this,
                "Dokument ma niezapisane zmiany. Zapisać?",
                "Notebook+",
                JOptionPane.YES_NO_CANCEL_OPTION
            );

            if (answer == JOptionPane.CANCEL_OPTION
                    || answer == JOptionPane.CLOSED_OPTION) {
                if (previous >= 0) tabs.setSelectedIndex(previous);
                return false;
            }

            if (answer == JOptionPane.YES_OPTION && !saveFile()) {
                if (previous >= 0) tabs.setSelectedIndex(previous);
                return false;
            }
        }

        if (previous >= 0 && previous < tabs.getTabCount()) {
            tabs.setSelectedIndex(previous);
        }

        return true;
    }

    private void closeTab() {
        Doc doc = selectedDoc();
        if (doc == null) return;

        if (doc.changed) {
            int answer = JOptionPane.showConfirmDialog(
                this,
                "Dokument ma niezapisane zmiany. Zapisać?",
                "Zamknij kartę",
                JOptionPane.YES_NO_CANCEL_OPTION
            );

            if (answer == JOptionPane.CANCEL_OPTION
                    || answer == JOptionPane.CLOSED_OPTION) {
                return;
            }

            if (answer == JOptionPane.YES_OPTION && !saveFile()) {
                return;
            }
        }

        tabs.removeTabAt(tabs.getSelectedIndex());

        if (tabs.getTabCount() == 0) {
            addNewTab();
        }

        updateStatus();
    }

    private void setTabName(Doc doc, String name) {
        int index = tabs.indexOfComponent(doc.scroll);

        if (index >= 0) {
            tabs.setTitleAt(index, name);
        }
    }

    private void undo() {
        Doc doc = selectedDoc();

        if (doc != null && doc.undo.canUndo()) {
            doc.undo.undo();
        }
    }

    private void redo() {
        Doc doc = selectedDoc();

        if (doc != null && doc.undo.canRedo()) {
            doc.undo.redo();
        }
    }

    private void findText() {
        String query = JOptionPane.showInputDialog(
            this, "Wpisz szukany tekst:"
        );

        if (query == null || query.isEmpty()) return;

        JTextArea area = editor();
        String text = area.getText();
        int start = Math.max(0, area.getCaretPosition());
        int found = text.indexOf(query, start);

        if (found < 0) {
            found = text.indexOf(query);
        }

        if (found >= 0) {
            area.requestFocus();
            area.select(found, found + query.length());
            status.setText(" Znaleziono tekst");
        } else {
            status.setText(" Nie znaleziono tekstu");
        }
    }

    private void replaceText() {
        JTextField search = new JTextField(20);
        JTextField replacement = new JTextField(20);

        Object[] fields = {
            "Szukaj:", search,
            "Zamień na:", replacement
        };

        int answer = JOptionPane.showConfirmDialog(
            this, fields, "Zamień tekst",
            JOptionPane.OK_CANCEL_OPTION
        );

        if (answer != JOptionPane.OK_OPTION) return;
        if (search.getText().isEmpty()) return;

        JTextArea area = editor();
        area.setText(
            area.getText().replace(search.getText(), replacement.getText())
        );
    }

    private void transform(Function<String, String> operation) {
        JTextArea area = editor();
        String selected = area.getSelectedText();

        if (selected == null) {
            area.setText(operation.apply(area.getText()));
        } else {
            area.replaceSelection(operation.apply(selected));
        }
    }

    private void removeEmptyLines() {
        String[] lines = editor().getText().split("\\R", -1);
        StringBuilder result = new StringBuilder();

        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                result.append(line).append('\n');
            }
        }

        editor().setText(result.toString());
    }

    private void clearEditor() {
        int answer = JOptionPane.showConfirmDialog(
            this,
            "Czy na pewno wyczyścić dokument?",
            "Wyczyść edytor",
            JOptionPane.YES_NO_OPTION
        );

        if (answer == JOptionPane.YES_OPTION) {
            editor().setText("");
        }
    }

    private void showStats() {
        Doc doc = selectedDoc();
        if (doc == null) return;

        try {
            File engine = new File("notebook_engine.exe");

            if (!engine.isFile()) {
                throw new FileNotFoundException(
                    "Nie znaleziono notebook_engine.exe w folderze programu."
                );
            }

            Process process = new ProcessBuilder(
                engine.getAbsolutePath(), "count"
            ).redirectErrorStream(true).start();

            try (OutputStream out = process.getOutputStream()) {
                out.write(doc.area.getText().getBytes(StandardCharsets.UTF_8));
            }

            String result;

            try (InputStream in = process.getInputStream()) {
                result = new String(
                    in.readAllBytes(), StandardCharsets.UTF_8
                );
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new IOException(result);
            }

            JOptionPane.showMessageDialog(
                this, result, "Statystyki tekstu — silnik C",
                JOptionPane.INFORMATION_MESSAGE
            );

        } catch (Exception ex) {
            showError(
                "Nie udało się uruchomić silnika C.\n\n"
                + ex.getMessage()
            );
        }
    }

    private void fileInfo() {
        Doc doc = selectedDoc();
        if (doc == null) return;

        String name = doc.file == null
            ? "Nowy dokument"
            : doc.file.getAbsolutePath();

        String text = doc.area.getText();

        JOptionPane.showMessageDialog(
            this,
            "Plik: " + name
            + "\nZnaki Java: " + text.length()
            + "\nLinie: " + doc.area.getLineCount()
            + "\nKodowanie zapisu: UTF-8",
            "Informacje o dokumencie",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void toggleTheme() {
        dark = !dark;
        applyTheme();
    }

    private void applyTheme() {
        Color background = dark
            ? new Color(30, 32, 38)
            : Color.WHITE;

        Color foreground = dark
            ? new Color(230, 232, 238)
            : Color.BLACK;

        for (int i = 0; i < tabs.getTabCount(); i++) {
            JScrollPane pane = (JScrollPane) tabs.getComponentAt(i);
            Doc doc = (Doc) pane.getClientProperty("document");

            if (doc != null) {
                applyThemeTo(doc);
            }
        }

        getContentPane().setBackground(background);
        status.setForeground(foreground);
        status.setOpaque(true);
        status.setBackground(background);
    }

    private void applyThemeTo(Doc doc) {
        Color background = dark
            ? new Color(30, 32, 38)
            : Color.WHITE;

        Color foreground = dark
            ? new Color(230, 232, 238)
            : Color.BLACK;

        doc.area.setBackground(background);
        doc.area.setForeground(foreground);
        doc.area.setCaretColor(foreground);

        doc.numbers.setBackground(
            dark ? new Color(38, 40, 46) : new Color(235, 237, 240)
        );

        doc.numbers.setForeground(
            dark ? new Color(130, 145, 165) : new Color(90, 95, 105)
        );
    }

    private void changeFont(int delta) {
        JTextArea area = editor();
        Font font = area.getFont();

        int size = Math.max(8, Math.min(40, font.getSize() + delta));
        area.setFont(font.deriveFont((float) size));
    }

    private void toggleWrap() {
        JTextArea area = editor();
        area.setLineWrap(!area.getLineWrap());
    }

    private void toggleLineNumbers() {
        Doc doc = selectedDoc();
        if (doc == null) return;

        doc.showNumbers = !doc.showNumbers;

        doc.scroll.setRowHeaderView(
            doc.showNumbers ? doc.numbers : null
        );
    }

    private void updateStatus() {
        Doc doc = selectedDoc();

        if (doc == null) {
            status.setText(" Gotowy");
            return;
        }

        int caret = doc.area.getCaretPosition();
        String before = doc.area.getText().substring(0, caret);
        int line = before.split("\n", -1).length;

        status.setText(
            " Linia: " + line
            + "   |   Znaki: " + doc.area.getText().length()
            + "   |   UTF-8"
        );
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(
            this, message, "Notebook+ — błąd",
            JOptionPane.ERROR_MESSAGE
        );
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            NotebookPlus app = new NotebookPlus();
            app.setVisible(true);
        });
    }
}
