package org.kpcc.reader;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class ArticleListFragment extends Fragment
{
    public final static String EXTRA_REQUEST_PARAMS = "org.kpcc.reader.request_params";
    public final static String EXTRA_LAST_PAGE = "org.kpcc.reader.last_page";

    private final static String TAG = "org.kpcc.reader.DEBUG.ArticleListFragment";
    private final static int LOAD_THRESHOLD = 0;
    private final static String QUERY_DEFAULT_TYPES = "news,blogs,segments";
    private final static String QUERY_DEFAULT_LIMIT = "20";
    private final static String QUERY_DEFAULT_PAGE = "1";

    private ArticleCollection mArticles;
    private GridView mGridView;
    private boolean mLoadingArticles = false;
    private RelativeLayout mLoadingIndicator;
    private int mLastPage = 0; // This gets updated on a successful page load.
    private HashMap<String, String> mParams = new HashMap<String, String>();
    private ArticleAdapter mAdapter;


    public static ArticleListFragment newInstance(HashMap<String, String> params)
    {
        Bundle args = new Bundle();

        if (params != null)
        {
            args.putSerializable(EXTRA_REQUEST_PARAMS, params);
        }

        ArticleListFragment fragment = new ArticleListFragment();

        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Setup default params
        mParams.put("types", QUERY_DEFAULT_TYPES);
        mParams.put("limit", QUERY_DEFAULT_LIMIT);
        mParams.put("page", QUERY_DEFAULT_PAGE);

        // Fill in params from passed-in arguments
        HashMap<String, String> params =
            (HashMap<String, String>) getArguments().getSerializable(EXTRA_REQUEST_PARAMS);

        mergeParams(mParams, params);

        // Get whatever the current Article set is.
        // It may be overridden when the HTTP query is finished.
        mArticles = ArticleCollection.get(getActivity());
        fetchArticles(null, true);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.activity_article_list, parent, false);

        mGridView = (GridView)v.findViewById(R.id.articles_GridView);
        mLoadingIndicator = (RelativeLayout)v.findViewById(R.id.article_list_loading_footer);

        if (mLoadingArticles)
        {
            mLoadingIndicator.setVisibility(View.VISIBLE);
        } else {
            mLoadingIndicator.setVisibility(View.GONE);
        }

        resetAdapter();

        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                Article a = ((ArticleAdapter) mGridView.getAdapter()).getItem(position);
                Intent i = new Intent(getActivity(), SingleArticleActivity.class);
                i.putExtra(SingleArticleFragment.EXTRA_ARTICLE_ID, a.getId());
                i.putExtra(EXTRA_REQUEST_PARAMS, mParams);
                i.putExtra(EXTRA_LAST_PAGE, mLastPage);
                startActivity(i);
            }
        });

        mGridView.setOnScrollListener(new AbsListView.OnScrollListener()
        {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState)
            {
                if (scrollState == SCROLL_STATE_IDLE)
                {
                    if (view.getLastVisiblePosition() >= view.getCount() - 1 - LOAD_THRESHOLD)
                    {
                        fetchArticles(nextPageParams(), false);
                    }
                }
            }

            @Override
            public void onScroll(
            AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
            {
            }
        });

        return v;
    }


    private void fetchArticles(HashMap<String, String> params, boolean replace)
    {
        // Add a loading mutex to prevent loading too much.
        // The lock gets released in the onSuccess callback.
        if (mLoadingArticles) return;
        setIsLoading(true);

        mergeParams(mParams, params);
        RequestParams requestParams = hashToParams(mParams);

        ArticleClient.getCollection(requestParams, new ArticleJsonResponseHandler(replace));
    }


    // Yes, these are two nearly identical methods. RequestParams and HashMap need to share
    // a roof under a single class so that they can be interchangeable in some regard,
    // like in these two methods.
    // Either that or RequestParams needs to be cast-able to a HashMap.
    private void mergeParams(HashMap<String, String> original, HashMap<String, String>... updaters)
    {
        for (HashMap<String, String> updater : updaters)
        {
            if (updater == null) continue;

            for (Map.Entry<String, String> entry : updater.entrySet())
            {
                original.put(entry.getKey(), entry.getValue());
            }
        }
    }


    private RequestParams hashToParams(HashMap<String, String> hash)
    {
        RequestParams params = new RequestParams();

        for (Map.Entry<String, String> entry : hash.entrySet())
        {
            params.put(entry.getKey(), entry.getValue());
        }

        return params;
    }


    private HashMap<String, String> nextPageParams()
    {
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("page", String.valueOf(mLastPage + 1));
        return params;
    }


    private void setIsLoading(boolean isLoading)
    {
        mLoadingArticles = isLoading;

        if (mLoadingIndicator != null)
        {
            mLoadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
    }


    private void resetAdapter()
    {
        if (getActivity() == null || mGridView == null) return;

        if (mArticles != null)
        {
            mAdapter = new ArticleAdapter(mArticles);
            mGridView.setAdapter(mAdapter);
        } else {
            mGridView.setAdapter(null);
        }
    }


    private class ArticleJsonResponseHandler extends JsonHttpResponseHandler
    {
        private boolean mShouldReplaceCollection;


        public ArticleJsonResponseHandler(boolean shouldReplaceCollection)
        {
            mShouldReplaceCollection = shouldReplaceCollection;
        }


        @Override
        public void onSuccess(JSONArray articles)
        {
            ArrayList<Article> collection = new ArrayList<Article>();

            try
            {
                for (int i = 0; i < articles.length(); i++)
                {
                    Article article = Article.buildFromJson(articles.getJSONObject(i));
                    collection.add(article);
                }
            } catch (JSONException e) {
                // TODO: Handle this error more nicely.
                e.printStackTrace();
            }

            // Update the ArticleCollection articles.
            if (mShouldReplaceCollection)
            {
                // We replace the app's entire collection of Articles,
                // and reset the adapter for this Activity.
                mArticles.setArticles(collection);
                resetAdapter();

                // Set the page back to 1.
                // TODO: This would be incorrect if someone requested a replace fetch with a different page than 1.
                mLastPage = 1;

            } else {
                // Just add the extra articles to this activity's adapter.
                // The adapter will handle adding them to the collection.
                // NOTE: We would use ArrayAdapter#addAll, but it requires API level 11+.
                for (Article article : collection) mAdapter.add(article);

                // We are *assuming* that if we get here (i.e. we're appending articles
                // to the full collection), this was a pagination request, so we'll
                // increase the page.
                // TODO: Find a better place to increase the page number.
                mLastPage += 1;
            }

            setIsLoading(false);
        }

        @Override
        public void onFailure(
            int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse)
        {
            // TODO: Handle errors mo' betta
            mLoadingArticles = false;
        }
    }


    private class ArticleAdapter extends ArrayAdapter<Article>
    {

        public ArticleAdapter(ArticleCollection articleCollection)
        {
            super(getActivity(), 0, articleCollection.getArticles());
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            if (convertView == null)
            {
                convertView = getActivity().getLayoutInflater()
                    .inflate(R.layout.list_item_article, null);
            }

            Article article = getItem(position);

            TextView titleTextView =
                (TextView)convertView.findViewById(
                    R.id.article_list_item_title_textView);

            ImageView assetImageView =
                    (ImageView) convertView.findViewById(
                            R.id.article_list_item_asset_ImageView);

            titleTextView.setText(article.getShortTitle());

            if (article.hasAssets())
            {
                String url = article.getAssets().get(0).getSizeSmall().getUrl();
                ImageLoader.getInstance().displayImage(url, assetImageView);
            }

            return convertView;
        }

    }

}
