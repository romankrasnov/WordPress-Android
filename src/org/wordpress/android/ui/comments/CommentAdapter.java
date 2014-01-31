package org.wordpress.android.ui.comments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.android.volley.toolbox.NetworkImageView;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.datasets.CommentTable;
import org.wordpress.android.models.Comment;
import org.wordpress.android.models.CommentList;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.DateTimeUtils;
import org.wordpress.android.util.SysUtils;

import java.util.HashSet;
import java.util.Iterator;

/**
 * Created by nbradbury on 1/29/14.
 */
public class CommentAdapter extends BaseAdapter {
    protected static interface OnLoadMoreListener {
        public void onLoadMore();
    }

    protected static interface OnSelectedItemsChangeListener {
        public void onSelectedItemsChanged();
    }

    private LayoutInflater mInflater;
    private OnLoadMoreListener mOnLoadMoreListener;
    private OnSelectedItemsChangeListener mOnSelectedChangeListener;
    private CommentList mComments = new CommentList();
    private HashSet<Integer> mSelectedPositions = new HashSet<Integer>();

    private int mStatusColorSpam;
    private int mStatusColorUnapproved;
    private int mAvatarSz;

    private String mStatusTextSpam;
    private String mStatusTextUnapproved;
    private String mAnonymous;

    private boolean mEnableSelection;
    private int mSelectedColor;
    private Drawable mDefaultAvatar;
    
    protected CommentAdapter(Context context,
                             OnLoadMoreListener onLoadMoreListener,
                             OnSelectedItemsChangeListener onChangeListener) {
        mInflater = LayoutInflater.from(context);

        mOnLoadMoreListener = onLoadMoreListener;
        mOnSelectedChangeListener = onChangeListener;

        final Resources resources = context.getResources();
        mStatusColorSpam = Color.RED;
        mStatusColorUnapproved = resources.getColor(R.color.orange_medium);
        mStatusTextSpam = resources.getString(R.string.spam);
        mStatusTextUnapproved = resources.getString(R.string.unapproved);
        mAnonymous = resources.getString(R.string.anonymous);

        mAvatarSz = resources.getDimensionPixelSize(R.dimen.avatar_sz_medium);
        mDefaultAvatar = resources.getDrawable(R.drawable.placeholder);
        mSelectedColor = resources.getColor(R.color.blue_extra_light);
    }

    @Override
    public int getCount() {
        return (mComments != null ? mComments.size() : 0);
    }

    @Override
    public Object getItem(int position) {
        return mComments.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    protected void clear() {
        if (mComments.size() > 0) {
            mComments.clear();
            notifyDataSetChanged();
        }
    }

    protected void setEnableSelection(boolean enable) {
        if (enable == mEnableSelection)
            return;

        mEnableSelection = enable;
        if (mEnableSelection) {
            notifyDataSetChanged();
        } else {
            clearSelectedComments();
        }
    }

    protected void clearSelectedComments() {
        if (mSelectedPositions.size() > 0) {
            mSelectedPositions.clear();
            notifyDataSetChanged();
            if (mOnSelectedChangeListener != null)
                mOnSelectedChangeListener.onSelectedItemsChanged();
        }
    }

    protected int getSelectedCommentCount() {
        return mSelectedPositions.size();
    }

    protected CommentList getSelectedComments() {
        CommentList comments = new CommentList();
        if (!mEnableSelection)
            return comments;

        Iterator it = mSelectedPositions.iterator();
        while (it.hasNext()) {
            int position = (Integer) it.next();
            if (isPositionValid(position))
                comments.add(mComments.get(position));
        }

        return comments;
    }

    protected boolean isItemSelected(int position) {
        return mSelectedPositions.contains(position);
    }

    protected void setItemSelected(int position, boolean isSelected) {
        if (isItemSelected(position) == isSelected)
            return;

        if (isSelected) {
            mSelectedPositions.add(position);
        } else {
            mSelectedPositions.remove(position);
        }

        notifyDataSetChanged();

        if (mOnSelectedChangeListener != null)
            mOnSelectedChangeListener.onSelectedItemsChanged();
    }

    protected void toggleItemSelected(int position) {
        setItemSelected(position, !isItemSelected(position));
    }

    private boolean isPositionValid(int position) {
        return (position >= 0 && position < mComments.size());
    }

    protected void replaceComments(final CommentList comments) {
        mComments.replaceComments(comments);
        notifyDataSetChanged();
    }

    protected void deleteComments(final CommentList comments) {
        mComments.deleteComments(comments);
        notifyDataSetChanged();
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        final Comment comment = mComments.get(position);
        final CommentHolder holder;

        if (convertView == null || convertView.getTag() == null) {
            convertView = mInflater.inflate(R.layout.comment_row, null);
            holder = new CommentHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (CommentHolder) convertView.getTag();
        }

        holder.txtName.setText(comment.hasAuthorName() ? comment.getAuthorName() : mAnonymous);
        holder.txtPostTitle.setText(comment.getUnescapedPostTitle());
        holder.txtComment.setText(comment.getUnescapedCommentText());
        holder.txtDate.setText(DateTimeUtils.javaDateToTimeSpan(comment.getDatePublished()));

        // status is only shown for comments that haven't been approved
        switch (comment.getStatusEnum()) {
            case SPAM :
                holder.txtStatus.setText(mStatusTextSpam);
                holder.txtStatus.setTextColor(mStatusColorSpam);
                holder.txtStatus.setVisibility(View.VISIBLE);
                break;
            case UNAPPROVED:
                holder.txtStatus.setText(mStatusTextUnapproved);
                holder.txtStatus.setTextColor(mStatusColorUnapproved);
                holder.txtStatus.setVisibility(View.VISIBLE);
                break;
            default :
                holder.txtStatus.setVisibility(View.GONE);
                break;
        }

        String avatarUrl = comment.getAvatarForDisplay(mAvatarSz);
        if (!TextUtils.isEmpty(avatarUrl)) {
            holder.imgAvatar.setImageUrl(avatarUrl, WordPress.imageLoader);
        } else {
            holder.imgAvatar.setImageDrawable(mDefaultAvatar);
        }

        if (mEnableSelection && isItemSelected(position)) {
            convertView.setBackgroundColor(mSelectedColor);
        } else {
            convertView.setBackgroundColor(Color.TRANSPARENT);
        }

        // request to load more comments when we near the end
        if (mOnLoadMoreListener != null && position >= getCount()-1)
            mOnLoadMoreListener.onLoadMore();

        return convertView;
    }

    private class CommentHolder {
        private TextView txtName;
        private TextView txtComment;
        private TextView txtStatus;
        private TextView txtPostTitle;
        private TextView txtDate;
        private NetworkImageView imgAvatar;

        private CommentHolder(View row) {
            txtName = (TextView) row.findViewById(R.id.name);
            txtComment = (TextView) row.findViewById(R.id.comment);
            txtStatus = (TextView) row.findViewById(R.id.status);
            txtPostTitle = (TextView) row.findViewById(R.id.postTitle);
            txtDate = (TextView) row.findViewById(R.id.text_date);
            imgAvatar = (NetworkImageView) row.findViewById(R.id.avatar);
            imgAvatar.setDefaultImageResId(R.drawable.placeholder);
        }
    }

    /*
     * load comments using an AsyncTask
     */
    @SuppressLint("NewApi")
    protected void loadComments() {
        if (mIsLoadTaskRunning)
            AppLog.w(AppLog.T.COMMENTS, "load comments task already active");

        if (SysUtils.canUseExecuteOnExecutor()) {
            new LoadCommentsTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            new LoadCommentsTask().execute();
        }
    }

    /*
     * AsyncTask to load comments from SQLite
     */
    private boolean mIsLoadTaskRunning = false;
    private class LoadCommentsTask extends AsyncTask<Void, Void, Boolean> {
        CommentList tmpComments;
        @Override
        protected void onPreExecute() {
            mIsLoadTaskRunning = true;
        }
        @Override
        protected void onCancelled() {
            mIsLoadTaskRunning = false;
        }
        @Override
        protected Boolean doInBackground(Void... params) {
            int localBlogId = WordPress.currentBlog.getLocalTableBlogId();
            tmpComments = CommentTable.getCommentsForBlog(localBlogId);
            if (mComments.isSameList(tmpComments))
                return false;

            // pre-calc transient values so they're cached when used by getView()
            for (Comment comment: tmpComments) {
                comment.getDatePublished();
                comment.getUnescapedCommentText();
                comment.getUnescapedPostTitle();
                comment.getAvatarForDisplay(mAvatarSz);
            }

            return true;
        }
        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                mComments = (CommentList)(tmpComments.clone());
                notifyDataSetChanged();
            }
            mIsLoadTaskRunning = false;
        }
    }
}
