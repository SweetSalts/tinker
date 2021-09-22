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

public class EventObserverAdapter implements EventObserver {

	public void onEvent(FlipCardEvent event) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void onEvent(DifficultySelectedEvent event) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void onEvent(HidePairCardsEvent event) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void onEvent(FlipDownCardsEvent event) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void onEvent(StartEvent event) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void onEvent(ThemeSelectedEvent event) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void onEvent(GameWonEvent event) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void onEvent(BackGameEvent event) {
		throw new UnsupportedOperationException();		
	}

	@Override
	public void onEvent(NextGameEvent event) {
		throw new UnsupportedOperationException();		
	}

	@Override
	public void onEvent(ResetBackgroundEvent event) {
		throw new UnsupportedOperationException();		
	}

}
