package com.bonitasoft.actorfilter

import org.bonitasoft.engine.api.APIAccessor
import org.bonitasoft.engine.api.IdentityAPI
import org.bonitasoft.engine.api.ProcessAPI
import org.bonitasoft.engine.connector.ConnectorValidationException
import org.bonitasoft.engine.connector.EngineExecutionContext
import org.bonitasoft.engine.identity.CustomUserInfo
import org.bonitasoft.engine.identity.impl.CustomUserInfoDefinitionImpl
import org.bonitasoft.engine.identity.impl.CustomUserInfoValueImpl
import spock.lang.Specification

class ActorByGeoAndTasksTest extends Specification {

    def ActorByGeoAndTasks filter
    APIAccessor apiAccessor = Mock(APIAccessor)
    ProcessAPI processApi = Mock(ProcessAPI)
    IdentityAPI identityAPI = Mock(IdentityAPI)
    EngineExecutionContext engineExecutionContext = Mock(EngineExecutionContext)

    def setup() {
        apiAccessor.getProcessAPI() >> processApi
        apiAccessor.getIdentityAPI() >> identityAPI
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

    def should_return_a_list_of_candidates() {
        given: 'Users with some task already assigned'
        processApi.getUserIdsForActor(_ as Long, 'MyActor', 0, Integer.MAX_VALUE) >> [1L, 2L, 3L]
        processApi.getNumberOfAssignedHumanTaskInstances(1L) >> 20L
        processApi.getNumberOfAssignedHumanTaskInstances(2L) >> 9L
        processApi.getNumberOfAssignedHumanTaskInstances(3L) >> 7L

        def geoDef = new CustomUserInfoDefinitionImpl(id: 456L, name: 'geo', description: null)
        def maxDef = new CustomUserInfoDefinitionImpl(id: 789L, name: 'maxTasks', description: null)
        def otherDef = new CustomUserInfoDefinitionImpl(id: 743L, name: 'other', description: null)

        apiAccessor.getIdentityAPI().getCustomUserInfoDefinitions(_, _) >> [geoDef, maxDef, otherDef]

        identityAPI.getCustomUserInfo(1L, _, _) >> {
            def argumentUserId = 1L
            [new CustomUserInfo(argumentUserId, geoDef,
                    new CustomUserInfoValueImpl(userId: argumentUserId, value: 'Africa', definitionId: geoDef.id)),
             new CustomUserInfo(argumentUserId, maxDef,
                     new CustomUserInfoValueImpl(userId: argumentUserId, value: '35', definitionId: maxDef.id)),]
        }

        identityAPI.getCustomUserInfo(2L, _, _) >> {
            def argumentUserId = 2L
            [new CustomUserInfo(argumentUserId, geoDef,
                    new CustomUserInfoValueImpl(userId: argumentUserId, value: 'Japan', definitionId: geoDef.id)),
             new CustomUserInfo(argumentUserId, maxDef,
                     new CustomUserInfoValueImpl(userId: argumentUserId, value: '12', definitionId: maxDef.id)),]
        }
        identityAPI.getCustomUserInfo(3L, _, _) >> {
            def argumentUserId = 3L
            [new CustomUserInfo(argumentUserId, geoDef,
                    new CustomUserInfoValueImpl(userId: argumentUserId, value: 'Japan', definitionId: geoDef.id)),
             new CustomUserInfo(argumentUserId, maxDef,
                     new CustomUserInfoValueImpl(userId: argumentUserId, value: '7', definitionId: maxDef.id)),]
        }

        and: 'An actor filter with a valid maximum workload'
        filter.setInputParameters([
                (ActorByGeoAndTasks.MAXIMUM_WORKLOAD_INPUT)   : 'maxTasks',
                (ActorByGeoAndTasks.GEO_ATTRIBUTE_NAME_INPUT) : 'geo',
                (ActorByGeoAndTasks.GEO_ATTRIBUTE_VALUE_INPUT): 'Japan',
        ])

        when: 'Applying filter to the existing users'
        filter.validateInputParameters()
        def candidates = filter.filter("MyActor")

        then: 'Only users with a workload below the maximum are returned as candidates'
        candidates == [2L]

    }
}