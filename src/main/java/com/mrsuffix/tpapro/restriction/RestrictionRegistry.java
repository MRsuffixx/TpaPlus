package com.mrsuffix.tpapro.restriction;

import com.mrsuffix.tpapro.api.model.RestrictionContext;
import com.mrsuffix.tpapro.api.service.CustomRestriction;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RestrictionRegistry {
    private final CopyOnWriteArrayList<CustomRestriction> restrictions = new CopyOnWriteArrayList<>();
    public AutoCloseable register(CustomRestriction restriction) {
        restrictions.add(Objects.requireNonNull(restriction, "restriction"));
        return () -> restrictions.remove(restriction);
    }
    public CustomRestriction.Result check(RestrictionContext context) {
        for (CustomRestriction restriction : restrictions) {
            CustomRestriction.Result result;
            try { result = restriction.check(context); }
            catch (RuntimeException failure) { return CustomRestriction.Result.deny("custom-restriction-error"); }
            if (result != null && !result.allowed()) return result;
        }
        return CustomRestriction.Result.allow();
    }
    public int size() { return restrictions.size(); }
}
