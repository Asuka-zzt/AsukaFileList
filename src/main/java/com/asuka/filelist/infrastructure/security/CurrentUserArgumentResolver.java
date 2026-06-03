package com.asuka.filelist.infrastructure.security;

import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Controller 方法参数中的 CurrentUser 解析器。
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * 支持直接声明 CurrentUser 参数。
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.isAssignableFrom(parameter.getParameterType());
    }

    /**
     * 从请求属性读取认证拦截器写入的当前用户。
     */
    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Object value = webRequest.getAttribute(AuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, 0);
        if (value instanceof CurrentUser currentUser) {
            return currentUser;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "Current user is unavailable");
    }
}
