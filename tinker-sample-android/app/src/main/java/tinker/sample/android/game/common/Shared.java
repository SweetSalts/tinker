package tinker.sample.android.game.common;

import android.content.Context;
import androidx.fragment.app.FragmentActivity;
import tinker.sample.android.game.engine.Engine;
import tinker.sample.android.game.events.EventBus;

public class Shared {

	public static Context context;
	public static FragmentActivity activity; // it's fine for this app, but better move to weak reference
	public static Engine engine;
	public static EventBus eventBus;

}
