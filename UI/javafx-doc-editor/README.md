# JavaFX Document Editor

This project is a JavaFX application that provides a simple document editor with a main menu and an editing interface. The application is designed to allow users to create new documents, browse existing ones, and collaborate in real-time.

## Project Structure

```
javafx-doc-editor
├── src
│   ├── main
│   │   ├── java
│   │   │   ├── app
│   │   │   │   ├── Main.java          # Entry point of the application
│   │   │   │   ├── MainMenuUI.java    # Main Menu UI definition
│   │   │   │   ├── EditorUI.java       # Document Editor UI definition
│   │   │   │   └── icons               # Contains icon representations
│   │   │   │       ├── DocumentPlusIcon.java
│   │   │   │       ├── DocumentIcon.java
│   │   │   │       ├── UndoIcon.java
│   │   │   │       ├── RedoIcon.java
│   │   │   │       └── UserIcon.java
│   │   └── resources
│   │       └── styles
│   │           └── app.css             # CSS styles for the application
├── README.md                             # Project documentation
└── pom.xml                               # Maven configuration file
```

## Features

- **Main Menu**: 
  - Create a new document.
  - Browse and import existing text files.
  - Enter a session code to join collaborative editing.

- **Document Editor**:
  - Control panel with Undo, Redo, and Export functionalities.
  - Display of viewer and editor codes with copy functionality.
  - Active users list with colorful usernames.
  - Text area for document editing with seamless scrolling and fixed-width font.

## Setup Instructions

1. **Clone the Repository**:
   ```bash
   git clone <repository-url>
   cd javafx-doc-editor
   ```

2. **Build the Project**:
   Ensure you have Maven installed, then run:
   ```bash
   mvn clean install
   ```

3. **Run the Application**:
   Execute the following command to start the application:
   ```bash
   mvn javafx:run
   ```

## Usage

- Launch the application to see the main menu.
- Click on "New Doc." to create a new document.
- Use "Browse.." to select a text file to edit.
- Enter a session code and click "Join" to collaborate with others.

## Contributing

Feel free to submit issues or pull requests for improvements and bug fixes. 

## License

This project is licensed under the MIT License. See the LICENSE file for details.