package com.yarvis.assistant.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yarvis.assistant.R;
import com.yarvis.assistant.network.WebSocketMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter para mostrar mensajes en el RecyclerView del chat.
 */
public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;
    private static final int TYPE_SYSTEM = 2;

    private final List<ChatMessageModel> messages = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private OnMessageClickListener clickListener;

    public interface OnMessageClickListener {
        void onMessageClick(ChatMessageModel message);
        void onLinkClick(String url);
    }

    public void setOnMessageClickListener(OnMessageClickListener listener) {
        this.clickListener = listener;
    }

    public void setMessages(List<ChatMessageModel> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessageModel message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateMessage(ChatMessageModel message) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(message.getId())) {
                messages.set(i, message);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void clearMessages() {
        messages.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessageModel message = messages.get(position);
        if (message.isFromUser()) {
            return TYPE_USER;
        } else if (message.isFromAssistant()) {
            return TYPE_ASSISTANT;
        } else {
            return TYPE_SYSTEM;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_USER:
                return new UserMessageViewHolder(
                        inflater.inflate(R.layout.item_message_user, parent, false));
            case TYPE_ASSISTANT:
                return new AssistantMessageViewHolder(
                        inflater.inflate(R.layout.item_message_assistant, parent, false));
            default:
                return new SystemMessageViewHolder(
                        inflater.inflate(R.layout.item_message_system, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessageModel message = messages.get(position);

        if (holder instanceof UserMessageViewHolder) {
            bindUserMessage((UserMessageViewHolder) holder, message);
        } else if (holder instanceof AssistantMessageViewHolder) {
            bindAssistantMessage((AssistantMessageViewHolder) holder, message);
        } else if (holder instanceof SystemMessageViewHolder) {
            bindSystemMessage((SystemMessageViewHolder) holder, message);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private void bindUserMessage(UserMessageViewHolder holder, ChatMessageModel message) {
        holder.textView.setText(message.getText());
        holder.timeView.setText(timeFormat.format(new Date(message.getTimestamp())));

        // Icono de voz si es mensaje de voz
        if (message.getType() == ChatMessageModel.MessageType.USER_VOICE) {
            holder.voiceIcon.setVisibility(View.VISIBLE);
        } else {
            holder.voiceIcon.setVisibility(View.GONE);
        }

        // Estado del mensaje
        switch (message.getStatus()) {
            case SENDING:
                holder.statusIcon.setImageResource(android.R.drawable.ic_popup_sync);
                holder.statusIcon.setVisibility(View.VISIBLE);
                break;
            case SENT:
                holder.statusIcon.setImageResource(android.R.drawable.ic_menu_send);
                holder.statusIcon.setVisibility(View.VISIBLE);
                break;
            case ERROR:
                holder.statusIcon.setImageResource(android.R.drawable.ic_dialog_alert);
                holder.statusIcon.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void bindAssistantMessage(AssistantMessageViewHolder holder, ChatMessageModel message) {
        // Texto - usar preview si está disponible
        String displayText = message.getPreviewText();
        holder.textView.setText(displayText);
        holder.timeView.setText(timeFormat.format(new Date(message.getTimestamp())));

        // Contenido enriquecido
        WebSocketMessage.ShowContent show = message.getShowContent();
        if (show != null) {
            // Imagen
            if (show.imageUrl != null) {
                holder.previewImage.setVisibility(View.VISIBLE);
                // TODO: Cargar imagen con Glide/Picasso
                // Glide.with(holder.itemView).load(show.imageUrl).into(holder.previewImage);
            } else {
                holder.previewImage.setVisibility(View.GONE);
            }

            // Links
            if (show.links != null && !show.links.isEmpty()) {
                holder.linksContainer.setVisibility(View.VISIBLE);
                holder.linksContainer.removeAllViews();

                for (WebSocketMessage.ShowLink link : show.links) {
                    TextView linkView = new TextView(holder.itemView.getContext());
                    linkView.setText("• " + link.title);
                    linkView.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.primary));
                    linkView.setPadding(0, 4, 0, 4);
                    linkView.setOnClickListener(v -> {
                        if (clickListener != null) {
                            clickListener.onLinkClick(link.url);
                        }
                    });
                    holder.linksContainer.addView(linkView);
                }
            } else {
                holder.linksContainer.setVisibility(View.GONE);
            }

            // Indicador de "ver más" si el texto completo es más largo
            if (message.getText() != null && message.getText().length() > 150) {
                holder.expandIndicator.setVisibility(View.VISIBLE);
            } else {
                holder.expandIndicator.setVisibility(View.GONE);
            }
        } else {
            holder.previewImage.setVisibility(View.GONE);
            holder.linksContainer.setVisibility(View.GONE);
            holder.expandIndicator.setVisibility(
                    message.getText() != null && message.getText().length() > 150 ? View.VISIBLE : View.GONE);
        }

        // Click para expandir
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onMessageClick(message);
            }
        });
    }

    private void bindSystemMessage(SystemMessageViewHolder holder, ChatMessageModel message) {
        holder.textView.setText(message.getText());
    }

    // ==================== ViewHolders ====================

    static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;
        final TextView timeView;
        final ImageView voiceIcon;
        final ImageView statusIcon;

        UserMessageViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.message_text);
            timeView = itemView.findViewById(R.id.message_time);
            voiceIcon = itemView.findViewById(R.id.icon_voice);
            statusIcon = itemView.findViewById(R.id.status_icon);
        }
    }

    static class AssistantMessageViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;
        final TextView timeView;
        final ImageView previewImage;
        final LinearLayout linksContainer;
        final TextView expandIndicator;

        AssistantMessageViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.message_text);
            timeView = itemView.findViewById(R.id.message_time);
            previewImage = itemView.findViewById(R.id.preview_image);
            linksContainer = itemView.findViewById(R.id.links_container);
            expandIndicator = itemView.findViewById(R.id.expand_indicator);
        }
    }

    static class SystemMessageViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;

        SystemMessageViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.message_text);
        }
    }
}
