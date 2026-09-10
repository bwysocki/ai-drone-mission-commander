package pl.stalostech.ai_drone_mission_commander.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.stalostech.ai_drone_mission_commander.simulation.exception.SimulationConflictException;
import pl.stalostech.ai_drone_mission_commander.simulation.exception.SimulationNotFoundException;

@RestControllerAdvice(assignableTypes = SimulationController.class)
public class SimulationExceptionHandler {
    @ExceptionHandler(SimulationNotFoundException.class)
    public ProblemDetail notFound(SimulationNotFoundException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(SimulationConflictException.class)
    public ProblemDetail conflict(SimulationConflictException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidInput(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }
}
