package com.alastorkaneki.discordwidget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class ConversationAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<Conversation> items = new ArrayList<>();

    public ConversationAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void replace(List<Conversation> conversations) {
        items.clear();
        items.addAll(conversations);
        notifyDataSetChanged();
    }

    public Conversation getConversation(int position) {
        return items.get(position);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Conversation getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).key.hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView == null
                ? inflater.inflate(R.layout.row_conversation, parent, false)
                : convertView;
        Conversation conversation = getItem(position);
        TextView title = view.findViewById(R.id.rowTitle);
        TextView preview = view.findViewById(R.id.rowPreview);
        TextView time = view.findViewById(R.id.rowTime);
        TextView source = view.findViewById(R.id.rowSource);
        title.setText(conversation.title);
        preview.setText(conversation.preview.isEmpty() ? parent.getContext().getString(R.string.no_preview) : conversation.preview);
        time.setText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(conversation.timestamp)));
        source.setText(Conversation.SOURCE_SOCIAL_DM.equals(conversation.source)
                ? R.string.oauth_dm
                : R.string.notification_source);
        return view;
    }
}
