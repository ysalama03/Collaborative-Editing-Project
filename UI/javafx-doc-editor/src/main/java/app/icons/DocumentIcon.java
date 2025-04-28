package app.icons;

import javafx.scene.image.Image;

public class DocumentIcon {
    private static final String ICON_PATH = "/app/icons/document_icon.png"; // Path to the document icon image
    private Image icon;

    public DocumentIcon() {
        this.icon = new Image(getClass().getResourceAsStream(ICON_PATH));
    }

    public Image getIcon() {
        return icon;
    }
}