package com.ds.datastore;

import java.util.UUID;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A request is defined as coming into scope when it is about to
 * enter the first servlet or filter in each web application, as
 * going out of scope when it exits the last servlet or the first
 * filter in the chain.
 * @see ServletRequestListener
 */
@WebListener
public class RequestListener implements ServletRequestListener{

    private Logger logger = LoggerFactory.getLogger(RequestListener.class);

    /**
     * The request is about to come into scope of the web application.
     * The default implementation is a NO-OP.
     * @param sre Information about the request
     */
    @Override
    public void requestInitialized(ServletRequestEvent sre){
        HttpServletRequest request = (HttpServletRequest) sre.getServletRequest();
        String requestID = request.getHeader("requestID");
        if(requestID == null) {
            requestID = String.valueOf(UUID.randomUUID());
        }
        request.setAttribute("requestID", requestID);
        logger.info("Call to {} from {}, Request Type: {}, Request {} received", request.getRequestURI(), request.getRemoteHost(), request.getMethod(), requestID);
    }

}
