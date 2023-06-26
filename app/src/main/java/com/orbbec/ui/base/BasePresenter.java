package com.orbbec.ui.base;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

public class BasePresenter<V extends IBaseView> {
    protected Reference<V> mReference;
    protected V mView;

    /**
     * Attach the view
     *
     * @param view the view that extends IBaseView
     */
    public void attachView(V view) {
        this.mReference = new WeakReference<>(view);
        this.mView = mReference.get();
    }

    /**
     * Detach the view
     */
    public void detachView() {
        if (null != mReference) {
            mReference.clear();
            mReference = null;
        }
        mView = null;
    }
}
