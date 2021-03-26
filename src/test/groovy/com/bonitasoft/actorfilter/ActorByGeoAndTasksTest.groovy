package com.bonitasoft.actorfilter

import org.bonitasoft.engine.api.APIAccessor
import org.bonitasoft.engine.api.ProcessAPI
import org.bonitasoft.engine.connector.ConnectorValidationException
import org.bonitasoft.engine.connector.EngineExecutionContext

import spock.lang.Specification


class ActorByGeoAndTasksTest extends Specification {

    def ActorByGeoAndTasks filter
    APIAccessor apiAccessor = Mock(APIAccessor)
    ProcessAPI processApi = Mock(ProcessAPI)
    EngineExecutionContext engineExecutionContext = Mock(EngineExecutionContext)

    def setup() {
        apiAccessor.getProcessAPI() >> processApi
        filter = new ActorByGeoAndTasks()
        filter.setAPIAccessor(apiAccessor)
        filter.setExecutionContext(engineExecutionContext)
    }

    def should_throw_exception_if_mandatory_input_is_missing() {
        given: 'An actorfilter without input'
        filter.setInputParameters([:])

        when: 'Validating inputs'
        filter.validateInputParameters()

        then: 'ConnectorValidationException is thrown'
        thrown ConnectorValidationException
    }

    def should_throw_exception_if_mandatory_input_is_not_positive_integer() {
        given: 'An actorfilter with a negative input'
        filter.setInputParameters([(ActorByGeoAndTasks.MAXIMUM_WORKLOAD_INPUT):-1])

        when: 'Validating inputs'
        filter.validateInputParameters()

        then: 'ConnectorValidationException is thrown'
        thrown ConnectorValidationException
    }

    def should_throw_exception_if_mandatory_input_is_not_an_integer() {
        given: 'An actorfilter with a String input'
        filter.setInputParameters([(ActorByGeoAndTasks.MAXIMUM_WORKLOAD_INPUT):'1'])

        when: 'Validating inputs'
        filter.validateInputParameters()

        then: 'ConnectorValidationException is thrown'
        thrown ConnectorValidationException
    }

    def should_return_a_list_of_candidates() {
        given: 'Users with some task already assigned'
        processApi.getUserIdsForActor(_ as Long, 'MyActor', 0, Integer.MAX_VALUE) >> [1L, 2L, 3L]
        processApi.getNumberOfAssignedHumanTaskInstances(1L) >> 2L
        processApi.getNumberOfAssignedHumanTaskInstances(2L) >> 3L
        processApi.getNumberOfAssignedHumanTaskInstances(3L) >> 0L

        and: 'An actor filter with a valid maximum workload'
        filter.setInputParameters([(ActorByGeoAndTasks.MAXIMUM_WORKLOAD_INPUT):'3'])

        when: 'Applying filter to the existing users'
        def candidates = filter.filter("MyActor");

        then: 'Only users with a workload below the maximum are returned as candidates'
        assert candidates.contains(1L)
        assert candidates.contains(3L)
        assert !candidates.contains(2L)
    }
}