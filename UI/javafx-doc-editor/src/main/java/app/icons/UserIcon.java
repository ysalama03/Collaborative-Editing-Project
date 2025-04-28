package app.icons;

import javafx.scene.image.Image;

public class UserIcon {
    private static final String ICON_PATH = "/app/icons/user_icon.png"; // Path to the user icon image

    public static Image getUserIcon() {
        return new Image(UserIcon.class.getResourceAsStream(ICON_PATH));
    }
}