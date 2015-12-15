package com.example.android.testing.espresso.BasicSample;

import android.support.test.espresso.UiController;
import android.support.test.espresso.ViewAction;
import android.support.test.espresso.matcher.ViewMatchers;
import android.view.View;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

public class GetRootView implements ViewAction {
    View rootView;

    @Override
    public Matcher<View> getConstraints() {
        return Matchers.allOf(ViewMatchers.isRoot());
    }

    @Override
    public String getDescription() {
        return "get root view";
    }

    @Override
    public void perform(UiController uiController, View view) {
      rootView = view;
    }

    public View getRootView() {
        return rootView;
    }
}
