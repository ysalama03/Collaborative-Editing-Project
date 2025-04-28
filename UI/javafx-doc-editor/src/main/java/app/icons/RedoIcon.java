package app.icons;

import javafx.scene.image.Image;

public class RedoIcon {
    private static final String ICON_PATH = "/app/icons/redo.png"; // Path to the redo icon image
    private Image image;

    public RedoIcon() {
        this.image = new Image(getClass().getResourceAsStream(ICON_PATH));
    }

    public Image getImage() {
        return image;
    }
}