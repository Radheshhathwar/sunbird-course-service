package controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LogHelper;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.HeaderParam;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestValidator;
import org.sunbird.common.responsecode.ResponseCode;

import com.fasterxml.jackson.databind.JsonNode;

import akka.util.Timeout;
import play.libs.F.Promise;
import play.mvc.Result;
/**
 * This controller will handler all the request related 
 * to learner state.
 * @author Manzarul
 */
public class LearnerController extends BaseController {
	private LogHelper logger = LogHelper.getInstance(LearnerController.class.getName());
	/**
	 * This method will provide list of enrolled courses
	 * for a user. User courses are stored in Cassandra db.
	 * @param uid user id for whom we need to collect all the courses.
	 * @return Result
	 */
	public Promise<Result> getEnrolledCourses(String uid) {
		try {
			String userId = request().getHeader(HeaderParam.X_Session_ID.getName());
			Map<String, Object> map = new HashMap<>();
			map.put(JsonKey.USER_ID, uid);
			map.put(JsonKey.REQUESTED_BY, userId);
			Request request = new Request();
			request.setEnv(getEnvironment());
			request.setRequest(map);
			request.setOperation(ActorOperations.GET_COURSE.getValue());
			request.setRequest(map);
			Timeout timeout = new Timeout(Akka_wait_time, TimeUnit.SECONDS);
			request.setRequest_id(ExecutionContext.getRequestId());
			Promise<Result> res = actorResponseHandler(getRemoteActor(),request,timeout,JsonKey.COURSES);
			return res;
		} catch (Exception e) {
			return Promise.<Result> pure(createCommonExceptionResponse(e));
		}
	}
	
	/**
	 * This method will be called when  user will
	 * enroll for a new course. 
	 * @return Result
	 */
	public Promise<Result> enrollCourse() {
		try {
			JsonNode requestData = request().body().asJson();
			logger.info(" get course request data=" + requestData);
			Request reqObj = (Request) mapper.RequestMapper.mapRequest(requestData, Request.class);
			RequestValidator.validateEnrollCourse(reqObj);
			reqObj.setRequest_id(ExecutionContext.getRequestId());
			reqObj.setOperation(ActorOperations.ENROLL_COURSE.getValue());
			reqObj.setEnv(getEnvironment());
			HashMap<String, Object> innerMap = new HashMap<>();
			innerMap.put(JsonKey.COURSE, reqObj.getRequest());
			innerMap.put(JsonKey.REQUESTED_BY,getUserIdByAuthToken(request().getHeader(HeaderParam.X_Authenticated_Userid.getName())));
			reqObj.setRequest(innerMap);
			Timeout timeout = new Timeout(Akka_wait_time, TimeUnit.SECONDS);
			Promise<Result> res = actorResponseHandler(getRemoteActor(),reqObj,timeout,null);
			return res;
		} catch (Exception e) {
			return Promise.<Result> pure(createCommonExceptionResponse(e));
		}
	}
	
	/**
	 *This method will provide list of user content state.
	 *Content refer user activity {started,half completed ,completed} 
	 *against TOC (table of content).
	 * @return Result
	 */
	public Promise<Result> getContentState() {
		try {
			JsonNode requestData = request().body().asJson();
			logger.info(" get course request data=" + requestData);
			Request reqObj = (Request) mapper.RequestMapper.mapRequest(requestData, Request.class);
			reqObj.setRequest_id(ExecutionContext.getRequestId());
			reqObj.setOperation(ActorOperations.GET_CONTENT.getValue());
			reqObj.setEnv(getEnvironment());
			Map<String, Object> innerMap = createRequest(reqObj);
			reqObj.setRequest(innerMap);
			Timeout timeout = new Timeout(Akka_wait_time, TimeUnit.SECONDS);
			Promise<Result> res = actorResponseHandler(getRemoteActor(), reqObj, timeout, JsonKey.CONTENT_LIST);
			return res;
		} catch (Exception e) {
			return Promise.<Result> pure(createCommonExceptionResponse(e));
		}

	}
	
	@SuppressWarnings("unchecked")
	private Map<String, Object> createRequest(Request reqObj){
		
		HashMap<String, Object> innerMap = new HashMap<>();
		innerMap.put(JsonKey.REQUESTED_BY,getUserIdByAuthToken(request().getHeader(HeaderParam.X_Authenticated_Userid.getName())));
		innerMap.put(JsonKey.USER_ID, reqObj.getRequest().get(JsonKey.USER_ID));
		
		if((null != reqObj.getRequest().get(JsonKey.CONTENT_IDS)) && (null == reqObj.getRequest().get(JsonKey.COURSE_IDS))){
			innerMap.put(JsonKey.CONTENT_IDS, reqObj.getRequest().get(JsonKey.CONTENT_IDS));
			return innerMap;
		}else if((null == reqObj.getRequest().get(JsonKey.CONTENT_IDS)) && (null != reqObj.getRequest().get(JsonKey.COURSE_IDS))){
			innerMap.put(JsonKey.COURSE_IDS, reqObj.getRequest().get(JsonKey.COURSE_IDS));
			return innerMap;
		}else if((null != reqObj.getRequest().get(JsonKey.CONTENT_IDS)) && (null != reqObj.getRequest().get(JsonKey.COURSE_IDS))){
			if((((List<String>)reqObj.getRequest().get(JsonKey.COURSE_IDS)).size() == 1) && ((List<String>)reqObj.getRequest().get(JsonKey.CONTENT_IDS)).size() >= 0){
				Map<String , Object> map = new HashMap<String , Object>();
				map.put(JsonKey.COURSE_ID, ((List<String>)reqObj.getRequest().get(JsonKey.COURSE_IDS)).get(0));
				map.put(JsonKey.CONTENT_IDS, (reqObj.getRequest().get(JsonKey.CONTENT_IDS)));
				innerMap.put(JsonKey.COURSE,map);
				return innerMap;
			}else{
				throw new ProjectCommonException(ResponseCode.invalidRequestData.getErrorCode(),ResponseCode.invalidRequestData.getErrorMessage(),
						ResponseCode.invalidRequestData.getResponseCode());
			}
		}
		return null;
	}
   
	/**
	 *This method will update learner current state with last 
	 *store state.
	 * @return Result
	 */
	public Promise<Result> updateContentState() {
		try {
		JsonNode requestData = request().body().asJson();
        logger.info(" get content request data=" + requestData);
        Request reqObj  = (Request) mapper.RequestMapper.mapRequest(requestData, Request.class);
        RequestValidator.validateUpdateContent(reqObj);
        reqObj.setOperation(ActorOperations.ADD_CONTENT.getValue());
        reqObj.setRequest_id(ExecutionContext.getRequestId());
        reqObj.setEnv(getEnvironment());
		HashMap<String, Object> innerMap = new HashMap<>();
		innerMap.put(JsonKey.CONTENTS, reqObj.getRequest().get(JsonKey.CONTENTS));
		innerMap.put(JsonKey.REQUESTED_BY,getUserIdByAuthToken(request().getHeader(HeaderParam.X_Authenticated_Userid.getName())));
		innerMap.put(JsonKey.USER_ID ,reqObj.getRequest().get(JsonKey.USER_ID));
		reqObj.setRequest(innerMap);
		Timeout timeout = new Timeout(Akka_wait_time, TimeUnit.SECONDS);
		Promise<Result> res = actorResponseHandler(getRemoteActor(), reqObj, timeout, null);
		return res;
		} catch (Exception e) {
			return Promise.<Result> pure(createCommonExceptionResponse(e));
		}
	}
	
	 /**
     * 
     * @param all
     * @return
     */
    public  Result preflight(String all) {
	response().setHeader("Access-Control-Allow-Origin", "*");
	response().setHeader("Allow", "*");
	response().setHeader("Access-Control-Allow-Methods",
			"POST, GET, PUT, DELETE, OPTIONS");
	response()
			.setHeader("Access-Control-Allow-Headers",
					"Origin, X-Requested-With, Content-Type, Accept, Referer, User-Agent,X-Consumer-ID,cid,ts,X-Device-ID,X-Authenticated-Userid,X-msgid,id");
	return ok();
}
	
}
