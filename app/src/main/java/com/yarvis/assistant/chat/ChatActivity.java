package com.yarvis.assistant.chat;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yarvis.assistant.R;
import com.yarvis.assistant.VoiceService;
import com.yarvis.assistant.network.WebSocketMessage;

/**
 * Activity para mostrar el historial completo de conversaciones y chatear.
 */
public class ChatActivity extends AppCompatActivity implements
        ChatHistoryManager.ChatHistoryListener,
        ChatMessageAdapter.OnMessageClickListener {

    private RecyclerView recyclerView;
    private ChatMessageAdapter adapter;
    private EditText inputMessage;
    private ImageButton btnSend;

    private ChatHistoryManager historyManager;
    private VoiceService voiceService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            VoiceService.LocalBinder localBinder = (VoiceService.LocalBinder) binder;
            voiceService = localBinder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            voiceService = null;
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        initViews();
        initHistoryManager();
        loadMessages();
    }

    private void initViews() {
        // Toolbar buttons
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        ImageButton btnClear = findViewById(R.id.btn_clear);
        btnClear.setOnClickListener(v -> showClearConfirmation());

        // RecyclerView
        recyclerView = findViewById(R.id.messages_recycler);
        adapter = new ChatMessageAdapter();
        adapter.setOnMessageClickListener(this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);  // Scroll al final
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // Input
        inputMessage = findViewById(R.id.input_message);
        inputMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        btnSend = findViewById(R.id.btn_send);
        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void initHistoryManager() {
        historyManager = ChatHistoryManager.getInstance(this);
        historyManager.addListener(this);
    }

    private void loadMessages() {
        adapter.setMessages(historyManager.getAllMessages());
        scrollToBottom();
    }

    private void sendMessage() {
        String text = inputMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        inputMessage.setText("");

        // Crear mensaje local
        String sessionId = serviceBound && voiceService != null ?
                voiceService.getWebSocketClient().getActiveSessionId() : null;

        ChatMessageModel message = ChatMessageModel.fromUserText(text, sessionId);
        historyManager.addMessage(message);

        // Enviar al backend
        if (serviceBound && voiceService != null) {
            voiceService.getWebSocketClient().sendChatMessage(text);
            historyManager.updateMessageStatus(message.getId(), ChatMessageModel.MessageStatus.SENT);
        } else {
            historyManager.updateMessageStatus(message.getId(), ChatMessageModel.MessageStatus.ERROR);
        }
    }

    private void scrollToBottom() {
        if (adapter.getItemCount() > 0) {
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    private void showClearConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_confirm_title)
                .setMessage(R.string.clear_confirm_message)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    historyManager.clearHistory();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showMessageDetail(ChatMessageModel message) {
        // Mostrar el mensaje completo en un diálogo
        new AlertDialog.Builder(this)
                .setMessage(message.getText())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ==================== ChatHistoryListener ====================

    @Override
    public void onMessageAdded(ChatMessageModel message) {
        runOnUiThread(() -> {
            adapter.addMessage(message);
            scrollToBottom();
        });
    }

    @Override
    public void onMessageUpdated(ChatMessageModel message) {
        runOnUiThread(() -> adapter.updateMessage(message));
    }

    @Override
    public void onHistoryCleared() {
        runOnUiThread(() -> adapter.clearMessages());
    }

    // ==================== OnMessageClickListener ====================

    @Override
    public void onMessageClick(ChatMessageModel message) {
        // Mostrar mensaje completo si es largo
        if (message.getText() != null && message.getText().length() > 150) {
            showMessageDetail(message);
        }
    }

    @Override
    public void onLinkClick(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            // URL inválida o no hay app para manejarla
        }
    }

    // ==================== Lifecycle ====================

    @Override
    protected void onStart() {
        super.onStart();
        // Bind al servicio para enviar mensajes
        Intent intent = new Intent(this, VoiceService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        historyManager.removeListener(this);
    }
}
