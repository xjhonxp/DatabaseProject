package pe.openstrategy.databaseproject.port.in;

import pe.openstrategy.databaseproject.application.usecase.ExtractionRequest;
import pe.openstrategy.databaseproject.application.usecase.ExtractionResult;

/**
 * Primary input port for the extraction use case.
 */
public interface ExtractionInputPort {
    
    ExtractionResult execute(ExtractionRequest request);
}