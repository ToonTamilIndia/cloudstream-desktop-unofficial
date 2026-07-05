package android.widget;

import android.content.Context;

@android.annotation.Stub
public class CompoundButton extends Button {
    private boolean checked;
    private OnCheckedChangeListener listener;
    private android.content.res.ColorStateList buttonTintList;

    public CompoundButton(Context context) { super(context); }

    public interface OnCheckedChangeListener {
        void onCheckedChanged(CompoundButton buttonView, boolean isChecked);
    }

    public void setChecked(boolean checked) {
        if (this.checked == checked) return;
        this.checked = checked;
        if (listener != null) listener.onCheckedChanged(this, checked);
    }

    public boolean isChecked() { return checked; }

    public void toggle() { setChecked(!checked); }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    public void setButtonTintList(android.content.res.ColorStateList tint) { buttonTintList = tint; }
    public android.content.res.ColorStateList getButtonTintList() { return buttonTintList; }
}
