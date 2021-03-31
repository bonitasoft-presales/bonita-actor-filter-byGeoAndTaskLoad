package com.bonitasoft.actorfilter

import groovy.util.logging.Slf4j
import org.bonitasoft.engine.api.APIAccessor
import org.bonitasoft.engine.connector.ConnectorValidationException
import org.bonitasoft.engine.filter.AbstractUserFilter
import org.bonitasoft.engine.filter.UserFilterException
import org.bonitasoft.engine.identity.CustomUserInfoDefinition

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
        def userIds = processAPI.getUserIdsForActor(getExecutionContext().getProcessDefinitionId(), actorName, 0, Integer.MAX_VALUE);

        List<CustomUserInfoDefinition> customUserInfoDefinitions = apiAccessor.getIdentityAPI().getCustomUserInfoDefinitions(0, 10)
        def geoDefinition = customUserInfoDefinitions.find { it.name == geoInputName }
        def maxTasksDefinition = customUserInfoDefinitions.find { it.name == maximumWorkload }

        def assignCandidates = []
        def geoCandidates = userIds.findAll { isGeoCandidate(it, apiAccessor, geoDefinition) }
        geoCandidates.each { userId ->
            def customUserInfos = apiAccessor.getIdentityAPI().getCustomUserInfo(userId, 0, customUserInfoDefinitions.size())
            def maxTaskAttribute = customUserInfos.find { (it.getDefinition().id == maxTasksDefinition.id) }
            long assigned = apiAccessor.getProcessAPI().getNumberOfAssignedHumanTaskInstances(userId)
            long maxTasks = maxTaskAttribute?.value?.toLong()
            assignCandidates.add([userId: userId, max: maxTasks, assigned: assigned, available: (maxTasks - assigned) as long])
        }
        log.info("found ${geoCandidates.size()} matching candidates [${geoInputName}=${geoInputValue}]")
        assignCandidates.sort { a, b ->
            a.available == b.available ? 0 : a.available > b.available ? -1 : 1
        }
        def taskCandidates = assignCandidates.findAll {
            it.available > 0
        }
        taskCandidates.each {
            log.debug("isMaxTaskCandidate : ${it.toString()}")
        }
        log.info("found ${taskCandidates.size()} matching candidates [${maximumWorkload} > assigned tasks]")
        if (taskCandidates.size()>0) {
            def matching = taskCandidates[0]
            log.info("return geo matching candidates")
            return [matching.userId]
        } else {
            log.info("return candidate ${matching.toString()} ")
            return geoCandidates
        }
    }

    boolean isGeoCandidate(Long userId, APIAccessor apiAccessor, geoDefinition) {
        def customUserInfos = apiAccessor.getIdentityAPI().getCustomUserInfo(userId, 0, 10)
        def geoAttribute = customUserInfos.find { (it.getDefinition().id == geoDefinition.id) }
        def candidate = (geoAttribute?.value) == geoInputValue
        log.debug "isGeoCandidate : user ${userId} | geoAttribute: ${geoAttribute?.value} | candidate:${candidate}"
        candidate
    }

    @Override
    boolean shouldAutoAssignTaskIfSingleResult() {
        return autoAssign
    }
}