package com.sya.tube.legacy;
import android.view.Window;
import com.thekirankumar.youtubeauto2.activity.MainCarActivity;
public interface CarUiHost {
    SearchController getSearchController();
    SearchController getCarInputController();
    Window getCarWindow();
    void addActivityCallback(MainCarActivity.ActivityCallbacks callback);
    void removeActivityCallback(MainCarActivity.ActivityCallbacks callback);
}
