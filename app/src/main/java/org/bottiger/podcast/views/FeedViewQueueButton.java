package org.bottiger.podcast.views;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.os.Build;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import org.bottiger.podcast.R;
import org.bottiger.podcast.SoundWaves;
import org.bottiger.podcast.playlist.Playlist;
import org.bottiger.podcast.provider.IEpisode;
import org.bottiger.podcast.service.PlayerService;

/**
 * Created by apl on 19-04-2015.
 *
 * This class is almost exclusively based on https://github.com/bottiger/crossview
 */
public class FeedViewQueueButton extends PlayerButtonView implements View.OnClickListener {

    /**
     * Flag to denote the "plus" configuration
     */
    public static final int FLAG_STATE_PLUS = 0;
    /**
     * Flag to denote the "cross" configuration
     */
    public static final int FLAG_STATE_CROSS = 1;

    private static final float ARC_TOP_START = 225;
    private static final float ARC_TOP_ANGLE = 45f;
    private static final float ARC_BOTTOM_START = 45f;
    private static final float ARC_BOTTOM_ANGLE = 45f;
    private static final float ARC_LEFT_START = 315f;
    private static final float ARC_LEFT_ANGLE = -135f; // sweep backwards
    private static final float ARC_RIGHT_START = 135f;
    private static final float ARC_RIGHT_ANGLE = -135f; // sweep backwards

    private static final long ANIMATION_DURATION_MS = 300l;

    private static final int DEFAULT_COLOR = Color.GRAY;
    private static final float DEFAULT_STROKE_WIDTH = 8f;

    // Arcs that define the set of all points between which the two lines are drawn
    // Names (top, bottom, etc) are from the reference point of the "plus" configuration.
    private Path mArcTop;
    private Path mArcBottom;
    private Path mArcLeft;
    private Path mArcRight;

    // Pre-compute arc lengths when layout changes
    private float mArcLengthTop;
    private float mArcLengthBottom;
    private float mArcLengthLeft;
    private float mArcLengthRight;

    private final Paint mPaint = new Paint();
    private int mColor = DEFAULT_COLOR;
    private RectF mRect;
    private PathMeasure mPathMeasure;

    private IEpisode mEpisode;

    private float[] mFromXY;
    private float[] mToXY;

    /**
     * Internal state flag for the drawn appearance, plus or cross.
     * The default starting position is "plus". This represents the real configuration, whereas
     * {@code mPercent} holds the frame-by-frame position when animating between
     * the states.
     */
    private int mState = FLAG_STATE_PLUS;

    /**
     * The percent value upon the arcs that line endpoints should be found
     * when drawing.
     */
    private float mPercent = 1f;

    private Context mContext;

    public FeedViewQueueButton(Context context) {
        super(context);
        init(context);
    }

    public FeedViewQueueButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
        readXmlAttributes(context, attrs);
    }

    public FeedViewQueueButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
        readXmlAttributes(context, attrs);
    }

    private void init(Context argContext) {
        mContext = argContext;
        setOnClickListener(this);

        mPaint.setAntiAlias(true);
        mPaint.setColor(mColor);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.SQUARE);
        mPaint.setStrokeWidth(DEFAULT_STROKE_WIDTH);
    }

    @Override
    public void onClick(View view) {
        IEpisode item = getEpisode();

        if (item == null)
            return;

        Playlist playlist = SoundWaves.getAppContext(getContext()).getPlaylist();

        if (playlist.contains(item)) {
            int position = playlist.getPosition(item);
            playlist.removeItem(position);
        } else {
            playlist.queue(item);
        }

        toggle();
    }

    public synchronized void setEpisode(IEpisode argEpisode, @PlayPauseButton.ButtonLocation int argLocation) {
        super.setEpisode(argEpisode);
        PlayerService ps = PlayerService.getInstance();
        if (ps != null) {
            Playlist playlist = ps.getPlaylist();
            if (playlist.contains(argEpisode)) {
                cross(0);
            }
        }
    }

    private void readXmlAttributes(Context context, AttributeSet attrs) {
        // Size will be used for width and height of the icon, plus the space in between
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.FeedViewQueueButton, 0, 0);
        try {
            //mColor = a.getColor(R.styleable.FeedViewQueueButton_lineColor, DEFAULT_COLOR);
            mColor = DEFAULT_COLOR;
        } finally {
            a.recycle();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        //super.onDraw(canvas);

        setPointFromPercent(mArcTop, mArcLengthTop, mPercent, mFromXY);
        setPointFromPercent(mArcBottom, mArcLengthBottom, mPercent, mToXY);

        canvas.drawLine(mFromXY[0], mFromXY[1], mToXY[0], mToXY[1], mPaint);

        setPointFromPercent(mArcLeft, mArcLengthLeft, mPercent, mFromXY);
        setPointFromPercent(mArcRight, mArcLengthRight, mPercent, mToXY);

        canvas.drawLine(mFromXY[0], mFromXY[1], mToXY[0], mToXY[1], mPaint);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed) {
            init();
            invalidate();
        }
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        init();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        super.setPaddingRelative(start, top, end, bottom);
        init();
    }

    public void setColor(int argb) {
        mColor = argb;
        mPaint.setColor(argb);
        invalidate();
    }


    /**
     * Tell this view to switch states from cross to plus, or back, using the default animation duration.
     * @return an integer flag that represents the new state after toggling.
     *         This will be either {@link #FLAG_STATE_PLUS} or {@link #FLAG_STATE_CROSS}
     */
    public int toggle() {
        return toggle(ANIMATION_DURATION_MS);
    }

    /**
     * Tell this view to switch states from cross to plus, or back.
     * @param animationDurationMS duration in milliseconds for the toggle animation
     * @return an integer flag that represents the new state after toggling.
     *         This will be either {@link #FLAG_STATE_PLUS} or {@link #FLAG_STATE_CROSS}
     */
    public int toggle(long animationDurationMS) {
        mState = mState == FLAG_STATE_PLUS? FLAG_STATE_CROSS : FLAG_STATE_PLUS;
        // invert percent, because state was just flipped
        mPercent = 1 - mPercent;
        ValueAnimator animator = ValueAnimator.ofFloat(mPercent, 1);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setDuration(animationDurationMS);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setPercent(animation.getAnimatedFraction());
            }
        });

        animator.start();
        return mState;
    }

    /**
     * Transition to "X"
     */
    public void cross() {
        cross(ANIMATION_DURATION_MS);
    }

    /**
     * Transition to "X" over the given animation duration
     * @param animationDurationMS
     */
    public void cross(long animationDurationMS) {
        if (mState == FLAG_STATE_CROSS) {
            return;
        }
        toggle(animationDurationMS);
    }

    /**
     * Transition to "+"
     */
    public void plus() {
        plus(ANIMATION_DURATION_MS);
    }

    /**
     * Transition to "+" over the given animation duration
     */
    public void plus(long animationDurationMS) {
        if (mState == FLAG_STATE_PLUS) {
            return;
        }
        toggle(animationDurationMS);
    }

    private void setPercent(float percent) {
        mPercent = percent;
        invalidate();
    }

    private void init() {
        mRect = new RectF();
        mRect.left = getPaddingLeft();
        mRect.right = getWidth() - getPaddingRight();
        mRect.top = getPaddingTop();
        mRect.bottom = getHeight() - getPaddingBottom();

        mPathMeasure = new PathMeasure();

        mArcTop = new Path();
        mArcTop.addArc(mRect, ARC_TOP_START, ARC_TOP_ANGLE);
        mPathMeasure.setPath(mArcTop, false);
        mArcLengthTop = mPathMeasure.getLength();

        mArcBottom = new Path();
        mArcBottom.addArc(mRect, ARC_BOTTOM_START, ARC_BOTTOM_ANGLE);
        mPathMeasure.setPath(mArcBottom, false);
        mArcLengthBottom = mPathMeasure.getLength();

        mArcLeft = new Path();
        mArcLeft.addArc(mRect, ARC_LEFT_START, ARC_LEFT_ANGLE);
        mPathMeasure.setPath(mArcLeft, false);
        mArcLengthLeft = mPathMeasure.getLength();

        mArcRight = new Path();
        mArcRight.addArc(mRect, ARC_RIGHT_START, ARC_RIGHT_ANGLE);
        mPathMeasure.setPath(mArcRight, false);
        mArcLengthRight = mPathMeasure.getLength();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            setBackground(null);
        }

        mFromXY = new float[]{0f, 0f};
        mToXY = new float[]{0f, 0f};
    }

    /**
     * Given some path and its length, find the point ([x,y]) on that path at
     * the given percentage of length.  Store the result in {@code points}.
     * @param path any path
     * @param length the length of {@code path}
     * @param percent the percentage along the path's length to find a point
     * @param points a float array of length 2, where the coordinates will be stored
     */
    private void setPointFromPercent(Path path, float length, float percent, float[] points) {
        float percentFromState = mState == FLAG_STATE_PLUS ? percent : 1 - percent;
        mPathMeasure.setPath(path, false);
        mPathMeasure.getPosTan(length * percentFromState, points, null);

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = getMeasuredWidth();
        setMeasuredDimension(width, width);
    }
}
