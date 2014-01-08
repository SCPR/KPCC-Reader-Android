package org.kpcc.reader;

import org.json.*;
import com.loopj.android.http.*;

import java.text.SimpleDateFormat;
import android.text.Html;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.util.Log;


public class ArticleFragment extends Fragment
{

    public static final String EXTRA_ARTICLE_ID = "org.kpcc.reader.article_id";
    private static final String TAG = "ArticleFragment";

    private Article mArticle;
    private TextView mTitle;
    private TextView mBody;
    private TextView mTimestamp;


    public static ArticleFragment newInstance(String articleId)
    {
        Bundle args = new Bundle();
        args.putString(EXTRA_ARTICLE_ID, articleId);

        ArticleFragment fragment = new ArticleFragment();
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        String articleId = getArguments().getString(EXTRA_ARTICLE_ID);
        mArticle = ArticleCollection.get(getActivity()).getArticle(articleId);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.fragment_article, parent, false);

        mTitle      = (TextView)v.findViewById(R.id.article_title_textView);
        mBody       = (TextView)v.findViewById(R.id.article_body_textView);
        mTimestamp  = (TextView)v.findViewById(R.id.article_timestamp_textView);

        mTitle.setText(mArticle.getTitle());
        mBody.setText(Html.fromHtml(mArticle.getBody()));

        SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy");
        mTimestamp.setText(sdf.format(mArticle.getTimestamp()));

        return v;
    }

}
