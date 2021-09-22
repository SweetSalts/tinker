package tinker.sample.android.game.events;

import tinker.sample.android.game.events.engine.FlipDownCardsEvent;
import tinker.sample.android.game.events.engine.GameWonEvent;
import tinker.sample.android.game.events.engine.HidePairCardsEvent;
import tinker.sample.android.game.events.ui.BackGameEvent;
import tinker.sample.android.game.events.ui.DifficultySelectedEvent;
import tinker.sample.android.game.events.ui.FlipCardEvent;
import tinker.sample.android.game.events.ui.NextGameEvent;
import tinker.sample.android.game.events.ui.ResetBackgroundEvent;
import tinker.sample.android.game.events.ui.StartEvent;
import tinker.sample.android.game.events.ui.ThemeSelectedEvent;

public interface EventObserver {

	void onEvent(FlipCardEvent event);

	void onEvent(DifficultySelectedEvent event);

	void onEvent(HidePairCardsEvent event);

	void onEvent(FlipDownCardsEvent event);

	void onEvent(StartEvent event);

	void onEvent(ThemeSelectedEvent event);

	void onEvent(GameWonEvent event);

	void onEvent(BackGameEvent event);

	void onEvent(NextGameEvent event);

	void onEvent(ResetBackgroundEvent event);

}
