package compman.compsrv.model.exceptions;


import compman.compsrv.model.commands.CommandDTO;

public final class CommandProcessingException extends Exception {
    private final CommandDTO command;

    public final CommandDTO getCommand() {
        return this.command;
    }

    public CommandProcessingException(String message, CommandDTO command) {
        super(message);
        this.command = command;
    }
}