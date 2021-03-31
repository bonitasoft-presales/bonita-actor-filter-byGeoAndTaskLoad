package com.bonitasoft.actorfilter

import groovy.util.logging.Slf4j
import org.bonitasoft.engine.api.APIAccessor
import org.bonitasoft.engine.connector.ConnectorValidationException
import org.bonitasoft.engine.filter.AbstractUserFilter
import org.bonitasoft.engine.filter.UserFilterException
import org.bonitasoft.engine.identity.CustomUserInfoDefinition
import org.bonitasoft.engine.identity.User

@Slf4j
class ActorByGeoAndTasks extends AbstractUserFilter {

//    def static final MAXIMUM_WORKLOAD_INPUT = "customUserMaxTaskName"
    def static final MAXIMUM_WORKLOAD_INPUT = "customUserMaxTaskName"
    def static final GEO_ATTRIBUTE_NAME_INPUT = "customUserInfoGeoName"
    def static final GEO_ATTRIBUTE_VALUE_INPUT = "customUserInfoGeoValue"
    def static final AUTO_ASSIGN_INPUT = "autoAssign"

    String maximumWorkload
    String geoInputName
    String geoInputValue
    Boolean autoAssign


    /**
     * Perform validation on the inputs defined on the actorfilter definition (src/main/resources/actorByGeoAndTasks.def)
     * You should:
     * - validate that mandatory inputs are presents
     * - validate that the content of the inputs is coherent with your use case (e.g: validate that a date is / isn't in the past ...)
     */
    @Override
    def void validateInputParameters() throws ConnectorValidationException {
        validateStringInputParameterIsNotNulOrEmpty(MAXIMUM_WORKLOAD_INPUT)
        validateStringInputParameterIsNotNulOrEmpty(GEO_ATTRIBUTE_NAME_INPUT)
        validateStringInputParameterIsNotNulOrEmpty(GEO_ATTRIBUTE_VALUE_INPUT)

    }

    @Override
    def List<Long> filter(String actorName) throws UserFilterException {
        maximumWorkload = getStringInputParameter(MAXIMUM_WORKLOAD_INPUT)
        geoInputName = getStringInputParameter(GEO_ATTRIBUTE_NAME_INPUT)
        geoInputValue = getStringInputParameter(GEO_ATTRIBUTE_VALUE_INPUT)
        geoInputValue = getStringInputParameter(GEO_ATTRIBUTE_VALUE_INPUT)
        autoAssign = getInputParameter(AUTO_ASSIGN_INPUT).toString().toBoolean()

        log.info "maximumWorkload = ${maximumWorkload}"
        log.info "geoInputName = ${geoInputName}"
        log.info "geoInputValue = ${geoInputValue}"
        log.info "autoAssign = ${autoAssign}"

        def apiAccessor = getAPIAccessor()
        def processAPI = apiAccessor.getProcessAPI()
        def users = processAPI.getUserIdsForActor(getExecutionContext().getProcessDefinitionId(), actorName, 0, Integer.MAX_VALUE);

        List<CustomUserInfoDefinition> customUserInfoDefinitions = apiAccessor.getIdentityAPI().getCustomUserInfoDefinitions(0, 10)
        def geoDefinition = customUserInfoDefinitions.find { it.name == geoInputName }
        def maxTasksDefinition = customUserInfoDefinitions.find { it.name == maximumWorkload }

        users.findAll { isCandidate(it, apiAccessor, geoDefinition, maxTasksDefinition) }
    }

    boolean isCandidate(Long userId, APIAccessor apiAccessor, geoDefinition, maxTasksDefinition) {
        def customUserInfos = apiAccessor.getIdentityAPI().getCustomUserInfo(userId, 0, 10)
        def geoAttribute = customUserInfos.find { (it.getDefinition().id == geoDefinition.id) }
        def maxTaskAttribute = customUserInfos.find { (it.getDefinition().id == maxTasksDefinition.id) }

        long maxTasks = maxTaskAttribute?.value?.toLong()
        long assigned = apiAccessor.getProcessAPI().getNumberOfAssignedHumanTaskInstances(userId)
        def candidate = (assigned < maxTasks) && ((geoAttribute?.value) == geoInputValue)

        log.debug "user ${userId} | assigned: ${assigned} | max: ${maxTasks} | geoAttribute: ${geoAttribute?.value} | candidate:${candidate}"
        candidate
    }

    @Override
    boolean shouldAutoAssignTaskIfSingleResult() {
        return autoAssign
    }
}