package com.bonitasoft.actorfilter

import org.bonitasoft.engine.api.APIAccessor
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.bonitasoft.engine.filter.AbstractUserFilter;
import org.bonitasoft.engine.filter.UserFilterException;
import org.bonitasoft.engine.identity.User;

import groovy.util.logging.Slf4j

@Slf4j
class ActorByGeoAndTasks extends AbstractUserFilter {
    
    def static final MAXIMUM_WORKLOAD_INPUT = "maximumWorkload"
    
    /**
     * Perform validation on the inputs defined on the actorfilter definition (src/main/resources/actorByGeoAndTasks.def)
     * You should:
     * - validate that mandatory inputs are presents
     * - validate that the content of the inputs is coherent with your use case (e.g: validate that a date is / isn't in the past ...)
     */
    @Override
    def void validateInputParameters() throws ConnectorValidationException {
        checkPositiveIntegerInput(MAXIMUM_WORKLOAD_INPUT)
    }

    def void checkPositiveIntegerInput(String inputName) throws ConnectorValidationException {
        def value = getInputParameter(inputName)
        if (value in Integer) {
            if (!value || value <= 0) {
                throw new ConnectorValidationException("Mandatory parameter '$inputName' must be a positive integer but is '$value'.")
            }
        } else {
            throw new ConnectorValidationException("'$inputName' parameter must be an Integer")
        }
    }
    
    /**
     * @return a list of {@link User} id that are the candidates to execute the task where this filter is defined.
     * If the result contains a unique user, the task will automaticaly be assigned.
     * @see AbstractUserFilter.shouldAutoAssignTaskIfSingleResult
     */
    @Override
    def List<Long> filter(String actorName) throws UserFilterException {
        def maximumWorkload = getInputParameter(MAXIMUM_WORKLOAD_INPUT)
        def apiAccessor = getAPIAccessor()
        def processAPI = apiAccessor.getProcessAPI()
        def users = processAPI.getUserIdsForActor(getExecutionContext().getProcessDefinitionId(), actorName, 0, Integer.MAX_VALUE);

        log.info "${MAXIMUM_WORKLOAD_INPUT} input = ${maximumWorkload}"
        users.findAll { !isOverloaded(it, apiAccessor, maximumWorkload as Integer) }
    }


    def boolean isOverloaded(Long userId, APIAccessor apiAccessor, int maximumWorkload) {
        apiAccessor.getProcessAPI().getNumberOfAssignedHumanTaskInstances(userId) >= maximumWorkload
    }
}