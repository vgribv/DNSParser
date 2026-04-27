package ru.vgribv.parser.bot.command;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CommandContainer {
    private final Map<String, Command> commandMap;
    private final UnknownCommand unknownCommand;

    public CommandContainer(List<Command> commands, UnknownCommand unknownCommand) {
        this.commandMap = commands.stream().collect(Collectors.toMap(Command::getCommandName, cmd -> cmd));
        this.unknownCommand = unknownCommand;
    }

    public Command retrieveCommand(String commandIdentifier) {
        return commandMap.getOrDefault(commandIdentifier, unknownCommand);
    }
}
