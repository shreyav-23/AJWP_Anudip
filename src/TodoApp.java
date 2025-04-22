import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;

public class TodoApp extends JFrame {
    private DefaultListModel<Task> taskListModel;
    private JList<Task> taskList;
    private JTextField taskInput;
    private JComboBox<String> categoryDropdown;
    private ButtonColor addButton, removeButton;

    private Connection conn;

    public TodoApp() {
        setTitle("To-Do List");
        setSize(500, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        TodoDb();
        TodoUI();
        loadTasks();
    }

    private void TodoUI() {

        Font font = new Font("Segoe UI", Font.PLAIN, 16);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(20, 20, 20, 20));
        setContentPane(root);

        JLabel title = new JLabel("To-Do List");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        root.add(title);
        root.add(Box.createVerticalStrut(20));

        taskListModel = new DefaultListModel<>();
        taskList = new JList<>(taskListModel);
        taskList.setCellRenderer(new TaskRenderer());
        taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scroll = new JScrollPane(taskList);
        root.add(scroll);
        root.add(Box.createVerticalStrut(20));

        taskList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = taskList.locationToIndex(e.getPoint());
                if (index != -1) {
                    Task task = taskListModel.get(index);
                    Rectangle bounds = taskList.getCellBounds(index, index);
                    if (e.getX() < bounds.x + 30) {
                        task.done = !task.done;
                        updateTaskDone(task);
                        loadTasks();
                    }
                }
            }
        });

        JPanel inputPanel = new JPanel(new BorderLayout(10, 10));
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),
                new EmptyBorder(15, 15, 15, 15)
        ));

        taskInput = new JTextField();
        taskInput.setFont(font);
        taskInput.setPreferredSize(new Dimension(3, 3));

        categoryDropdown = new JComboBox<>(new String[]{"General", "Work", "Personal", "Doo Later", "Urgent"});
        categoryDropdown.setFont(font);

        JPanel topInput = new JPanel(new BorderLayout(10, 10));
        topInput.add(taskInput, BorderLayout.CENTER);
        topInput.add(categoryDropdown, BorderLayout.EAST);

        addButton = new ButtonColor("Add Task");

        inputPanel.add(topInput, BorderLayout.CENTER);
        inputPanel.add(addButton, BorderLayout.SOUTH);
        root.add(inputPanel);
        root.add(Box.createVerticalStrut(15));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));

        ButtonColor editButton = new ButtonColor("Edit Selected Task");
        removeButton = new ButtonColor("Remove Selected Task");

        btnRow.add(editButton);
        btnRow.add(removeButton);
        root.add(btnRow);

        addButton.addActionListener(e -> addTask());
        removeButton.addActionListener(e -> removeTask());
        editButton.addActionListener(e -> editTask());
    }

    private void TodoDb() {
        String url = "jdbc:mysql://localhost:3306/todo_app";
        String user = "root";
        String password = "23Shreya";

        try {
            conn = DriverManager.getConnection(url, user, password);
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS tasks (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "description VARCHAR(255) NOT NULL," +
                    "done BOOLEAN DEFAULT FALSE," +
                    "category VARCHAR(50) DEFAULT 'General')");
        } catch (SQLException e) {
            showError("DB Error:\n" + e.getMessage());
        }
    }

    private void loadTasks() {
        taskListModel.clear();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM tasks")) {
            while (rs.next()) {
                Task task = new Task(
                        rs.getInt("id"),
                        rs.getString("description"),
                        rs.getBoolean("done"),
                        rs.getString("category")
                );
                taskListModel.addElement(task);
            }
        } catch (SQLException e) {
            showError("Failed to load tasks:\n" + e.getMessage());
        }
    }

    private void addTask() {
        String desc = taskInput.getText().trim();
        if (desc.isEmpty()) return;

        String category = (String) categoryDropdown.getSelectedItem();

        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO tasks (description, category) VALUES (?, ?)")) {
            ps.setString(1, desc);
            ps.setString(2, category);
            ps.executeUpdate();
            taskInput.setText("");
            loadTasks();
        } catch (SQLException e) {
            showError("Failed to add task:\n" + e.getMessage());
        }
    }

    private void removeTask() {
        int index = taskList.getSelectedIndex();
        if (index == -1) return;

        Task task = taskListModel.get(index);
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM tasks WHERE id = ?")) {
            ps.setInt(1, task.id);
            ps.executeUpdate();
            loadTasks();
        } catch (SQLException e) {
            showError("Failed to remove task:\n" + e.getMessage());
        }
    }

    private void updateTaskDone(Task task) {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE tasks SET done = ? WHERE id = ?")) {
            ps.setBoolean(1, task.done);
            ps.setInt(2, task.id);
            ps.executeUpdate();
        } catch (SQLException e) {
            showError("Failed to update task:\n" + e.getMessage());
        }
    }

    private void editTask() {
        int index = taskList.getSelectedIndex();
        if (index == -1) return;

        Task task = taskListModel.get(index);

        String newDesc = JOptionPane.showInputDialog(this, "Edit task description:", task.description);
        if (newDesc != null && !newDesc.trim().isEmpty()) {
            task.description = newDesc.trim();

            try (PreparedStatement ps = conn.prepareStatement("UPDATE tasks SET description = ? WHERE id = ?")) {
                ps.setString(1, task.description);
                ps.setInt(2, task.id);
                ps.executeUpdate();
                loadTasks();
            } catch (SQLException e) {
                showError("Failed to edit task:\n" + e.getMessage());
            }
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TodoApp().setVisible(true));
    }

    static class Task {
        int id;
        String description;
        boolean done;
        String category;

        Task(int id, String description, boolean done, String category) {
            this.id = id;
            this.description = description;
            this.done = done;
            this.category = category;
        }

        public String toString() {
            return description + " (" + category + ")";
        }
    }

    static class TaskRenderer extends JPanel implements ListCellRenderer<Task> {
        private JCheckBox checkBox;
        private JLabel label;

        public TaskRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(10, 15, 10, 15));
            setOpaque(true);

            checkBox = new JCheckBox();
            checkBox.setOpaque(false);
            checkBox.setEnabled(false);

            label = new JLabel();
            label.setFont(new Font("Segoe UI", Font.PLAIN, 16));

            add(checkBox, BorderLayout.WEST);
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Task> list, Task value, int index, boolean isSelected, boolean cellHasFocus) {
            checkBox.setSelected(value.done);
            label.setText("<html><b>" + value.description + "</b> <span>[" + value.category + "]</span></html>");
            setBackground(isSelected ? UIManager.getColor("List.selectionBackground") : UIManager.getColor("List.background"));
            return this;
        }
    }

    class ButtonColor extends JButton {
        public ButtonColor(String text) {
            super(text);
            setFont(new Font("Segoe UI", Font.BOLD, 16));
            setContentAreaFilled(true);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
    }
}
