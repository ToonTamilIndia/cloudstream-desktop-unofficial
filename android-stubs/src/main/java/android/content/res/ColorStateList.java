package android.content.res;

@android.annotation.Stub
public class ColorStateList {
    private final int color;

    public ColorStateList(int[][] states, int[] colors) {
        this.color = colors != null && colors.length > 0 ? colors[0] : 0;
    }

    private ColorStateList(int color) { this.color = color; }

    public static ColorStateList valueOf(int color) { return new ColorStateList(color); }
    public int getDefaultColor() { return color; }
    public int getColorForState(int[] stateSet, int defaultColor) { return color; }
    public boolean isStateful() { return false; }
}
