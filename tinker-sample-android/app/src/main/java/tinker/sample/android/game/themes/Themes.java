package tinker.sample.android.game.themes;

import android.graphics.Bitmap;
import java.util.ArrayList;
import tinker.sample.android.game.common.Shared;
import tinker.sample.android.game.utils.GameUtils;

public class Themes {

	public static String URI_DRAWABLE = "drawable://";

	public static Theme createAnimalsTheme() {
		Theme theme = new Theme();
		theme.id = 1;
		theme.name = "Animals";
		theme.backgroundImageUrl = URI_DRAWABLE + "back_animals";
		theme.tileImageUrls = new ArrayList<String>();
		// 40 drawables
		for (int i = 1; i <= 28; i++) {
			theme.tileImageUrls.add(URI_DRAWABLE + String.format("animals_%d", i));
		}
		return theme;
	}

	public static Theme createMosterTheme() {
		Theme theme = new Theme();
		theme.id = 2;
		theme.name = "Mosters";
		theme.backgroundImageUrl = URI_DRAWABLE + "back_horror";
		theme.tileImageUrls = new ArrayList<String>();
		// 40 drawables
		for (int i = 1; i <= 40; i++) {
			theme.tileImageUrls.add(URI_DRAWABLE + String.format("mosters_%d", i));
		}
		return theme;
	}

	public static Theme createEmojiTheme() {
		Theme theme = new Theme();
		theme.id = 3;
		theme.name = "Emoji";
		theme.backgroundImageUrl = URI_DRAWABLE + "background";
		theme.tileImageUrls = new ArrayList<String>();
		// 40 drawables
		for (int i = 1; i <= 40; i++) {
			theme.tileImageUrls.add(URI_DRAWABLE + String.format("emoji_%d", i));
		}
		return theme;
	}
	
	public static Bitmap getBackgroundImage(Theme theme) {
		String drawableResourceName = theme.backgroundImageUrl.substring(Themes.URI_DRAWABLE.length());
		int drawableResourceId = Shared.context.getResources().getIdentifier(drawableResourceName, "drawable", Shared.context.getPackageName());
		Bitmap bitmap = GameUtils.scaleDown(drawableResourceId, GameUtils.screenWidth(), GameUtils.screenHeight());
		return bitmap;
	}

}
