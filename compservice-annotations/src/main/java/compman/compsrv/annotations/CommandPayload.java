package compman.compsrv.annotations;

import compman.compsrv.model.commands.CommandType;
import compman.compsrv.model.events.EventType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface CommandPayload {
    CommandType[] type() default CommandType.DUMMY_COMMAND;
}
