package app.icons;

import javafx.scene.image.Image;

public class UndoIcon {
    private static final String ICON_PATH = "/app/icons/undo.png"; // Path to the undo icon image
    private Image icon;

    public UndoIcon() {
        this.icon = new Image(getClass().getResourceAsStream(ICON_PATH));
    }

    public Image getIcon() {
        return icon;
    }
}