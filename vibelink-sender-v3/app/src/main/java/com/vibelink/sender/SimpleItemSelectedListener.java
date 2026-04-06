package com.vibelink.sender;

import android.view.View;
import android.widget.AdapterView;

public class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {

    public interface ItemSelectionCallback {
        void onSelected(int position);
    }

    private final ItemSelectionCallback callback;

    public SimpleItemSelectedListener(ItemSelectionCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        callback.onSelected(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}
}
