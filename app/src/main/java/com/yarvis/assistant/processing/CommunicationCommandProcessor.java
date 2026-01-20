package com.yarvis.assistant.processing;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import com.yarvis.assistant.processing.CommandType.CommunicationCommand;

public class CommunicationCommandProcessor extends CommandProcessor<CommunicationCommand> {

    private static final String TAG = "CommCommandProcessor";

    public CommunicationCommandProcessor(Context context) {
        super(context);
    }

    @Override
    public String getProcessorName() {
        return "CommunicationProcessor";
    }

    @Override
    public boolean canHandle(CommandType command) {
        return command instanceof CommunicationCommand;
    }

    @Override
    protected boolean validate(CommunicationCommand command) {
        if (!super.validate(command)) return false;
        return command.getRecipient() != null && !command.getRecipient().isEmpty();
    }

    @Override
    protected CommandResult execute(CommunicationCommand command) throws Exception {
        Context context = getContext();
        if (context == null) {
            return CommandResult.failure(command.getId(), "Context not available");
        }

        Log.d(TAG, "Executing communication: " + command.getCommType() + " to " + command.getRecipient());

        switch (command.getCommType()) {
            case CALL:
                return makeCall(command);

            case SMS:
                return sendSms(command);

            case EMAIL:
                return sendEmail(command);

            case WHATSAPP:
                return sendWhatsApp(command);

            default:
                return CommandResult.failure(command.getId(),
                        "Tipo de comunicación no soportado: " + command.getCommType());
        }
    }

    private CommandResult makeCall(CommunicationCommand command) {
        Context context = getContext();
        if (context == null) {
            return CommandResult.failure(command.getId(), "Context not available");
        }

        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + command.getRecipient()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
            return CommandResult.success(command.getId(), "Llamando a " + command.getRecipient());
        }
        return CommandResult.failure(command.getId(), "No se puede realizar la llamada");
    }

    private CommandResult sendSms(CommunicationCommand command) {
        Context context = getContext();
        if (context == null) {
            return CommandResult.failure(command.getId(), "Context not available");
        }

        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + command.getRecipient()));
        if (command.getMessage() != null) {
            intent.putExtra("sms_body", command.getMessage());
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
            return CommandResult.success(command.getId(), "Abriendo SMS para " + command.getRecipient());
        }
        return CommandResult.failure(command.getId(), "No se puede enviar SMS");
    }

    private CommandResult sendEmail(CommunicationCommand command) {
        Context context = getContext();
        if (context == null) {
            return CommandResult.failure(command.getId(), "Context not available");
        }

        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + command.getRecipient()));
        if (command.getMessage() != null) {
            intent.putExtra(Intent.EXTRA_TEXT, command.getMessage());
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
            return CommandResult.success(command.getId(), "Abriendo email para " + command.getRecipient());
        }
        return CommandResult.failure(command.getId(), "No se puede enviar email");
    }

    private CommandResult sendWhatsApp(CommunicationCommand command) {
        Context context = getContext();
        if (context == null) {
            return CommandResult.failure(command.getId(), "Context not available");
        }

        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            return CommandResult.failure(command.getId(), "WhatsApp no está instalado");
        }

        String url = "https://api.whatsapp.com/send?phone=" + command.getRecipient();
        if (command.getMessage() != null) {
            url += "&text=" + Uri.encode(command.getMessage());
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        return CommandResult.success(command.getId(), "Abriendo WhatsApp para " + command.getRecipient());
    }
}
