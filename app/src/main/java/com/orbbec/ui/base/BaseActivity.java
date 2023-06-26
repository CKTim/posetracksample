package com.orbbec.ui.base;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity<P extends BasePresenter, V extends IBaseView> extends AppCompatActivity {
    protected P mPresenter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutId());
        mPresenter = createPresenter();
        mPresenter.attachView((V) this);
        initViews();
    }

    /**
     * Init views
     */
    protected abstract void initViews();

    /**
     * Finds a view that was identified by the id attribute from the XML that was processed in onCreate.
     *
     * @param id  the id of a view
     * @param <T>
     * @return the view identified by the id
     */
    public <T extends View> T find(@IdRes int id) {
        return findViewById(id);
    }

    /**
     * Get the id of layout
     *
     * @return id of layout
     */
    protected abstract int getLayoutId();

    /**
     * Create presenter
     *
     * @return presenter
     */
    protected abstract P createPresenter();

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.detachView();
    }
}
