package com.mrsuffix.tpapro.api.service;

import com.mrsuffix.tpapro.api.model.RestrictionContext;

@FunctionalInterface
public interface CustomRestriction {
    Result check(RestrictionContext context);
    record Result(boolean allowed, String reasonKey) {
        public static Result allow() { return new Result(true, ""); }
        public static Result deny(String reasonKey) { return new Result(false, reasonKey == null ? "custom" : reasonKey); }
    }
}
