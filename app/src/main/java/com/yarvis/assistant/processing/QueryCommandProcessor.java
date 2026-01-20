package com.yarvis.assistant.processing;

import android.content.Context;
import android.util.Log;

import com.yarvis.assistant.processing.CommandType.QueryCommand;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class QueryCommandProcessor extends CommandProcessor<QueryCommand> {

    private static final String TAG = "QueryCommandProcessor";

    public QueryCommandProcessor(Context context) {
        super(context);
    }

    @Override
    public String getProcessorName() {
        return "QueryProcessor";
    }

    @Override
    public boolean canHandle(CommandType command) {
        return command instanceof QueryCommand;
    }

    @Override
    protected CommandResult execute(QueryCommand command) throws Exception {
        Log.d(TAG, "Executing query: " + command.getQueryType() + " - " + command.getQuery());

        switch (command.getQueryType()) {
            case TIME:
                return getTime(command);

            case DATE:
                return getDate(command);

            case WEATHER:
                return getWeather(command);

            case CALENDAR:
                return getCalendarInfo(command);

            case REMINDER:
                return handleReminder(command);

            case GENERAL:
            default:
                return handleGeneralQuery(command);
        }
    }

    private CommandResult getTime(QueryCommand command) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String time = sdf.format(new Date());
        return CommandResult.success(command.getId(), "Son las " + time);
    }

    private CommandResult getDate(QueryCommand command) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));
        String date = sdf.format(new Date());
        return CommandResult.success(command.getId(), "Hoy es " + date);
    }

    private CommandResult getWeather(QueryCommand command) {
        return CommandResult.success(command.getId(),
                "El clima requiere conexión al backend. Query: " + command.getQuery());
    }

    private CommandResult getCalendarInfo(QueryCommand command) {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int weekOfYear = cal.get(Calendar.WEEK_OF_YEAR);
        int dayOfYear = cal.get(Calendar.DAY_OF_YEAR);

        String info = String.format(Locale.getDefault(),
                "Semana %d del año. Día %d de 365.", weekOfYear, dayOfYear);
        return CommandResult.success(command.getId(), info);
    }

    private CommandResult handleReminder(QueryCommand command) {
        return CommandResult.success(command.getId(),
                "Recordatorio: " + command.getQuery() + " (requiere integración con calendario)");
    }

    private CommandResult handleGeneralQuery(QueryCommand command) {
        return CommandResult.success(command.getId(),
                "Consulta general enviada al backend: " + command.getQuery());
    }
}
