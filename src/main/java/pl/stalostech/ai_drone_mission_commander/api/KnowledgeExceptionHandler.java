package pl.stalostech.ai_drone_mission_commander.api;

import org.springframework.http.HttpStatus;
import pl.stalostech.ai_drone_mission_commander.rag.exception.InvalidKnowledgeQueryException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = KnowledgeController.class)
public class KnowledgeExceptionHandler {
    @ExceptionHandler(InvalidKnowledgeQueryException.class)
    public ProblemDetail invalidQuery() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Provide a nonblank query (up to 2000 characters), topK from 1 to 6 and similarityThreshold from 0 to 1.");
    }
}
