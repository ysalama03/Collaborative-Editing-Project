package app.icons;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class DocumentPlusIcon {
    private static final String ICON_PATH = "/app/icons/document_plus.png"; // Path to the icon image

    public static ImageView createIcon() {
        ImageView icon = new ImageView(new Image(DocumentPlusIcon.class.getResourceAsStream(ICON_PATH)));
        icon.setFitWidth(24); // Set desired width
        icon.setFitHeight(24); // Set desired height
        return icon;
    }
}