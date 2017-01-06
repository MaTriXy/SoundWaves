package org.bottiger.podcast.webservices.directories.itunes;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.text.TextUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.bottiger.podcast.R;
import org.bottiger.podcast.provider.SlimImplementations.SlimSubscription;
import org.bottiger.podcast.utils.ErrorUtils;
import org.bottiger.podcast.utils.rxbus.RxBasicSubscriber;
import org.bottiger.podcast.webservices.directories.ISearchParameters;
import org.bottiger.podcast.webservices.directories.ISearchResult;
import org.bottiger.podcast.webservices.directories.generic.GenericDirectory;
import org.bottiger.podcast.webservices.directories.generic.GenericSearchResult;
import org.bottiger.podcast.webservices.directories.itunes.types.Entry;
import org.bottiger.podcast.webservices.directories.itunes.types.Feed;
import org.bottiger.podcast.webservices.directories.itunes.types.FeedLookup;
import org.bottiger.podcast.webservices.directories.itunes.types.TopList;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by apl on 13-04-2015.
 *
 * API: https://www.apple.com/itunes/affiliates/resources/documentation/itunes-store-web-service-search-api.html
 * Searching for "potter" => "https://itunes.apple.com/search?term=potter&entity=podcast";
 *
 * This class is based on examples from:
 * https://github.com/FasterXML/jackson-databind/
 * http://stackoverflow.com/questions/17495405/parsing-an-array-of-json-objects-using-jackson-2-0
 * http://www.journaldev.com/2324/jackson-json-processing-api-in-java-example-tutorial
 */
public class ITunes extends GenericDirectory {

    private static final String NAME = ITunes.class.getSimpleName();
    private static final String ENCODING = "UTF-8";

    private static final String jsonTest = "{\n" +
            " \"resultCount\":50,\n" +
            " \"results\": [\n" +
            "{\"wrapperType\":\"track\", \"kind\":\"podcast\", \"collectionId\":76699727, \"trackId\":76699727, \"artistName\":\"MuggleNet.com and Hypable.com\", \"collectionName\":\"MuggleCast: the Harry Potter podcast\", \"trackName\":\"MuggleCast: the Harry Potter podcast\", \"collectionCensoredName\":\"MuggleCast: the Harry Potter podcast\", \"trackCensoredName\":\"MuggleCast: the Harry Potter podcast\", \"collectionViewUrl\":\"https://itunes.apple.com/us/podcast/mugglecast-harry-potter-podcast/id76699727?mt=2&uo=4\", \"feedUrl\":\"http://podcasts.hypable.com/mugglecast/mugglecast.rss\", \"trackViewUrl\":\"https://itunes.apple.com/us/podcast/mugglecast-harry-potter-podcast/id76699727?mt=2&uo=4\", \"artworkUrl30\":\"http://is2.mzstatic.com/image/pf/us/r30/Podcasts6/v4/9e/61/f1/9e61f154-f272-9fbc-148d-70d07fd3bc5d/mza_2466246694932927218.30x30-50.jpg\", \"artworkUrl60\":\"http://is1.mzstatic.com/image/pf/us/r30/Podcasts6/v4/9e/61/f1/9e61f154-f272-9fbc-148d-70d07fd3bc5d/mza_2466246694932927218.60x60-50.jpg\", \"artworkUrl100\":\"http://is4.mzstatic.com/image/pf/us/r30/Podcasts6/v4/9e/61/f1/9e61f154-f272-9fbc-148d-70d07fd3bc5d/mza_2466246694932927218.100x100-75.jpg\", \"collectionPrice\":0.00, \"trackPrice\":0.00, \"trackRentalPrice\":0, \"collectionHdPrice\":0, \"trackHdPrice\":0, \"trackHdRentalPrice\":0, \"releaseDate\":\"2015-04-12T15:00:00Z\", \"collectionExplicitness\":\"notExplicit\", \"trackExplicitness\":\"notExplicit\", \"trackCount\":66, \"country\":\"USA\", \"currency\":\"USD\", \"primaryGenreName\":\"Literature\", \"radioStationUrl\":\"https://itunes.apple.com/station/idra.76699727\", \"artworkUrl600\":\"http://is5.mzstatic.com/image/pf/us/r30/Podcasts6/v4/9e/61/f1/9e61f154-f272-9fbc-148d-70d07fd3bc5d/mza_2466246694932927218.600x600-75.jpg\", \"genreIds\":[\"1401\", \"26\", \"1301\", \"1309\", \"1305\"], \"genres\":[\"Literature\", \"Podcasts\", \"Arts\", \"TV & Film\", \"Kids & Family\"]}, \n" +
            "{\"wrapperType\":\"track\", \"kind\":\"podcast\", \"artistId\":256201037, \"collectionId\":79138340, \"trackId\":79138340, \"artistName\":\"The Leaky Cauldron\", \"collectionName\":\"PotterCast: #1 Harry Potter Podcast\", \"trackName\":\"PotterCast: #1 Harry Potter Podcast\", \"collectionCensoredName\":\"PotterCast: #1 Harry Potter Podcast\", \"trackCensoredName\":\"PotterCast: #1 Harry Potter Podcast\", \"artistViewUrl\":\"https://itunes.apple.com/us/artist/wizzard-media/id256201037?mt=2&uo=4\", \"collectionViewUrl\":\"https://itunes.apple.com/us/podcast/pottercast-1-harry-potter/id79138340?mt=2&uo=4\", \"feedUrl\":\"http://www.the-leaky-cauldron.org/podcasts/pottercast.rss\", \"trackViewUrl\":\"https://itunes.apple.com/us/podcast/pottercast-1-harry-potter/id79138340?mt=2&uo=4\", \"artworkUrl30\":\"http://is4.mzstatic.com/image/pf/us/r30/Podcasts/2f/6f/ae/fdr.darafayf.30x30-50.jpg\", \"artworkUrl60\":\"http://is3.mzstatic.com/image/pf/us/r30/Podcasts/2f/6f/ae/fdr.darafayf.60x60-50.jpg\", \"artworkUrl100\":\"http://is4.mzstatic.com/image/pf/us/r30/Podcasts/2f/6f/ae/fdr.darafayf.100x100-75.jpg\", \"collectionPrice\":0.00, \"trackPrice\":0.00, \"trackRentalPrice\":0, \"collectionHdPrice\":0, \"trackHdPrice\":0, \"trackHdRentalPrice\":0, \"releaseDate\":\"2014-10-08T14:30:00Z\", \"collectionExplicitness\":\"notExplicit\", \"trackExplicitness\":\"notExplicit\", \"trackCount\":277, \"country\":\"USA\", \"currency\":\"USD\", \"primaryGenreName\":\"Literature\", \"radioStationUrl\":\"https://itunes.apple.com/station/idra.79138340\", \"artworkUrl600\":\"http://is2.mzstatic.com/image/pf/us/r30/Podcasts/2f/6f/ae/fdr.darafayf.600x600-75.jpg\", \"genreIds\":[\"1401\", \"26\", \"1301\", \"1309\"], \"genres\":[\"Literature\", \"Podcasts\", \"Arts\", \"TV & Film\"]}, \n" +
            "{\"wrapperType\":\"track\", \"kind\":\"podcast\", \"artistId\":443477631, \"collectionId\":338709955, \"trackId\":338709955, \"artistName\":\"Warner Bros. Digital Distribution\", \"collectionName\":\"The Pitfalls of Teenage Love - Harry Potter and the Half-Blood Prince Exclusive!\", \"trackName\":\"The Pitfalls of Teenage Love - Harry Potter and the Half-Blood Prince Exclusive!\", \"collectionCensoredName\":\"The Pitfalls of Teenage Love - Harry Potter and the Half-Blood Prince Exclusive!\", \"trackCensoredName\":\"The Pitfalls of Teenage Love - Harry Potter and the Half-Blood Prince Exclusive!\", \"artistViewUrl\":\"https://itunes.apple.com/us/artist/warner-bros./id443477631?mt=2&uo=4\", \"collectionViewUrl\":\"https://itunes.apple.com/us/podcast/pitfalls-teenage-love-harry/id338709955?mt=2&uo=4\", \"feedUrl\":\"http://pdl.warnerbros.com/wbol/us/dd/podcasts/harry_potter_6_teenage_love/hp6_teenage_love_main.xml\", \"trackViewUrl\":\"https://itunes.apple.com/us/podcast/pitfalls-teenage-love-harry/id338709955?mt=2&uo=4\", \"artworkUrl30\":\"http://is1.mzstatic.com/image/pf/us/r30/Podcasts/46/fe/6c/ps.ifsashlp.30x30-50.jpg\", \"artworkUrl60\":\"http://is5.mzstatic.com/image/pf/us/r30/Podcasts/46/fe/6c/ps.ifsashlp.60x60-50.jpg\", \"artworkUrl100\":\"http://is5.mzstatic.com/image/pf/us/r30/Podcasts/46/fe/6c/ps.ifsashlp.100x100-75.jpg\", \"collectionPrice\":0.00, \"trackPrice\":0.00, \"trackRentalPrice\":0, \"collectionHdPrice\":0, \"trackHdPrice\":0, \"trackHdRentalPrice\":0, \"releaseDate\":\"2009-11-03T09:00:00Z\", \"collectionExplicitness\":\"notExplicit\", \"trackExplicitness\":\"notExplicit\", \"trackCount\":1, \"country\":\"USA\", \"currency\":\"USD\", \"primaryGenreName\":\"TV & Film\", \"radioStationUrl\":\"https://itunes.apple.com/station/idra.338709955\", \"artworkUrl600\":\"http://is1.mzstatic.com/image/pf/us/r30/Podcasts/46/fe/6c/ps.ifsashlp.600x600-75.jpg\", \"genreIds\":[\"1309\", \"26\"], \"genres\":[\"TV & Film\", \"Podcasts\"]}, \n" +
            "{\"wrapperType\":\"track\", \"kind\":\"podcast\", \"collectionId\":455903834, \"trackId\":455903834, \"artistName\":\"Alexandra Williams\", \"collectionName\":\"Lumos: A Harry Potter Podcast\", \"trackName\":\"Lumos: A Harry Potter Podcast\", \"collectionCensoredName\":\"Lumos: A Harry Potter Podcast\", \"trackCensoredName\":\"Lumos: A Harry Potter Podcast\", \"collectionViewUrl\":\"https://itunes.apple.com/us/podcast/lumos-a-harry-potter-podcast/id455903834?mt=2&uo=4\", \"feedUrl\":\"http://lumos.podomatic.com/rss2.xml\", \"trackViewUrl\":\"https://itunes.apple.com/us/podcast/lumos-a-harry-potter-podcast/id455903834?mt=2&uo=4\", \"artworkUrl30\":\"http://is5.mzstatic.com/image/pf/us/r30/Podcasts/v4/1e/df/79/1edf79e7-d99d-98ed-4946-99c8e0fd510e/mza_7767382941851988473.30x30-50.jpg\", \"artworkUrl60\":\"http://is4.mzstatic.com/image/pf/us/r30/Podcasts/v4/1e/df/79/1edf79e7-d99d-98ed-4946-99c8e0fd510e/mza_7767382941851988473.60x60-50.jpg\", \"artworkUrl100\":\"http://is2.mzstatic.com/image/pf/us/r30/Podcasts/v4/1e/df/79/1edf79e7-d99d-98ed-4946-99c8e0fd510e/mza_7767382941851988473.100x100-75.jpg\", \"collectionPrice\":0.00, \"trackPrice\":0.00, \"trackRentalPrice\":0, \"collectionHdPrice\":0, \"trackHdPrice\":0, \"trackHdRentalPrice\":0, \"releaseDate\":\"2011-08-13T08:21:00Z\", \"collectionExplicitness\":\"notExplicit\", \"trackExplicitness\":\"notExplicit\", \"trackCount\":2, \"country\":\"USA\", \"currency\":\"USD\", \"primaryGenreName\":\"Literature\", \"radioStationUrl\":\"https://itunes.apple.com/station/idra.455903834\", \"artworkUrl600\":\"http://is3.mzstatic.com/image/pf/us/r30/Podcasts/v4/1e/df/79/1edf79e7-d99d-98ed-4946-99c8e0fd510e/mza_7767382941851988473.600x600-75.jpg\", \"genreIds\":[\"1401\", \"26\", \"1301\"], \"genres\":[\"Literature\", \"Podcasts\", \"Arts\"]}, \n" +
            "{\"wrapperType\":\"track\", \"kind\":\"podcast\", \"collectionId\":434847168, \"trackId\":434847168, \"artistName\":\"The Potter's House Family Worship Center\", \"collectionName\":\"The Potter's House Family Worship Center\", \"trackName\":\"The Potter's House Family Worship Center\", \"collectionCensoredName\":\"The Potter's House Family Worship Center\", \"trackCensoredName\":\"The Potter's House Family Worship Center\", \"collectionViewUrl\":\"https://itunes.apple.com/us/podcast/potters-house-family-worship/id434847168?mt=2&uo=4\", \"feedUrl\":\"http://pottershousefwc.org/podcast.php?pageID=5\", \"trackViewUrl\":\"https://itunes.apple.com/us/podcast/potters-house-family-worship/id434847168?mt=2&uo=4\", \"artworkUrl30\":\"http://is2.mzstatic.com/image/pf/us/r30/Podcasts/v4/81/66/cd/8166cdfb-b970-0891-d0cc-52048841c52d/mza_659765286684719219.30x30-50.jpg\", \"artworkUrl60\":\"http://is1.mzstatic.com/image/pf/us/r30/Podcasts/v4/81/66/cd/8166cdfb-b970-0891-d0cc-52048841c52d/mza_659765286684719219.60x60-50.jpg\", \"artworkUrl100\":\"http://is3.mzstatic.com/image/pf/us/r30/Podcasts/v4/81/66/cd/8166cdfb-b970-0891-d0cc-52048841c52d/mza_659765286684719219.100x100-75.jpg\", \"collectionPrice\":0.00, \"trackPrice\":0.00, \"trackRentalPrice\":0, \"collectionHdPrice\":0, \"trackHdPrice\":0, \"trackHdRentalPrice\":0, \"releaseDate\":\"2015-04-01T16:00:00Z\", \"collectionExplicitness\":\"notExplicit\", \"trackExplicitness\":\"notExplicit\", \"trackCount\":30, \"country\":\"USA\", \"currency\":\"USD\", \"primaryGenreName\":\"Christianity\", \"radioStationUrl\":\"https://itunes.apple.com/station/idra.434847168\", \"artworkUrl600\":\"http://is4.mzstatic.com/image/pf/us/r30/Podcasts/v4/81/66/cd/8166cdfb-b970-0891-d0cc-52048841c52d/mza_659765286684719219.600x600-75.jpg\", \"genreIds\":[\"1439\", \"26\", \"1314\", \"1444\"], \"genres\":[\"Christianity\", \"Podcasts\", \"Religion & Spirituality\", \"Spirituality\"]}, \n" +
            "{\"wrapperType\":\"track\", \"kind\":\"podcast\", \"collectionId\":914396881, \"trackId\":914396881, \"artistName\":\"Sadie Bean: Awesome Book Reviews | Harry Potter, Hobbit, Magic Half, Junie B Jones, and much more every week!\", \"collectionName\":\"Between Two Worlds with Sadie: Books for kids | Jokes | Fun Facts\", \"trackName\":\"Between Two Worlds with Sadie: Books for kids | Jokes | Fun Facts\", \"collectionCensoredName\":\"Between Two Worlds with Sadie: Books for kids | Jokes | Fun Facts\", \"trackCensoredName\":\"Between Two Worlds with Sadie: Books for kids | Jokes | Fun Facts\", \"collectionViewUrl\":\"https://itunes.apple.com/us/podcast/between-two-worlds-sadie-books/id914396881?mt=2&uo=4\", \"feedUrl\":\"http://feeds.feedburner.com/betweentwoworldspodcast\", \"trackViewUrl\":\"https://itunes.apple.com/us/podcast/between-two-worlds-sadie-books/id914396881?mt=2&uo=4\", \"artworkUrl30\":\"http://is1.mzstatic.com/image/pf/us/r30/Podcasts4/v4/ef/43/f2/ef43f2d8-2409-ae04-6428-e652f6544dfa/mza_2918338993485566970.30x30-50.jpg\", \"artworkUrl60\":\"http://is5.mzstatic.com/image/pf/us/r30/Podcasts4/v4/ef/43/f2/ef43f2d8-2409-ae04-6428-e652f6544dfa/mza_2918338993485566970.60x60-50.jpg\", \"artworkUrl100\":\"http://is4.mzstatic.com/image/pf/us/r30/Podcasts4/v4/ef/43/f2/ef43f2d8-2409-ae04-6428-e652f6544dfa/mza_2918338993485566970.100x100-75.jpg\", \"collectionPrice\":0.00, \"trackPrice\":0.00, \"trackRentalPrice\":0, \"collectionHdPrice\":0, \"trackHdPrice\":0, \"trackHdRentalPrice\":0, \"releaseDate\":\"2015-01-27T01:36:00Z\", \"collectionExplicitness\":\"cleaned\", \"trackExplicitness\":\"cleaned\", \"trackCount\":16, \"country\":\"USA\", \"currency\":\"USD\", \"primaryGenreName\":\"Kids & Family\", \"contentAdvisoryRating\":\"Clean\", \"radioStationUrl\":\"https://itunes.apple.com/station/idra.914396881\", \"artworkUrl600\":\"http://is5.mzstatic.com/image/pf/us/r30/Podcasts4/v4/ef/43/f2/ef43f2d8-2409-ae04-6428-e652f6544dfa/mza_2918338993485566970.600x600-75.jpg\", \"genreIds\":[\"1305\", \"26\", \"1304\", \"1415\", \"1301\", \"1401\"], \"genres\":[\"Kids & Family\", \"Podcasts\", \"Education\", \"K-12\", \"Arts\", \"Literature\"]}]\n" +
            "}";

    private static final int[] SUPPORTED_MODES = { POPULAR };

    private static final boolean INCLUDE_EXPLICIT = true;

    private static final String BASE_URL = "https://itunes.apple.com/search?term=";
    private static final String PODCAST_FILTER = "&entity=podcast";
    private static final String EXPLICIT = "&explicit=Yes";

    private static final String API_URL = "https://itunes.apple.com/";
    private ITunesEndpoint mService;

    private static final String QUERY_SEPARATOR = " ";

    private ObjectMapper mapper = new ObjectMapper(); // create once, reuse
    private QueryITunes asyncTask = null;

    public ITunes(@NonNull Context argContext) {
        super(NAME, argContext);
        // to prevent exception when encountering unknown property:
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // to allow coercion of JSON empty String ("") to null Object value:
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(API_URL)
                .client(getOkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        mService = retrofit.create(ITunesEndpoint.class);
    }

    public static @StringRes int getNameRes() {
        return R.string.webservices_discovery_engine_itunes;
    }

    @Override
    public void search(@NonNull ISearchParameters argParameters, @NonNull Callback argCallback) {
        String searchTerm;
        try {
            searchTerm = generateTerm(argParameters);
        } catch (UnsupportedEncodingException e) {
            argCallback.error(e);
            return;
        }

        if (TextUtils.isEmpty(searchTerm)) {
            return;
        }

        String urlString = generateUrl(searchTerm);

        URL url;

        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            argCallback.error(e);
            return;
        }

        asyncTask = new QueryITunes(url, searchTerm, argCallback);
        asyncTask.execute();
    }

    @Override
    public int[] supportedListModes() {
        return SUPPORTED_MODES;
    }

    @Override
    public void toplist(int amount, @Nullable String argTag, @NonNull final Callback argCallback) {
        String localLanguage = Locale.getDefault().getCountry().toLowerCase();

        mService.chart(amount, localLanguage).enqueue(new retrofit2.Callback<TopList>() {
            @Override
            public void onResponse(Call<TopList> call, final retrofit2.Response<TopList> response) {
                Flowable.just(response)
                        .subscribeOn(Schedulers.io())
                        .map(new Function<retrofit2.Response<TopList>, ISearchResult>() {
                            @Override
                            public ISearchResult apply(retrofit2.Response<TopList> chartResponse) throws Exception {
                                GenericSearchResult resultReturn = new GenericSearchResult("");

                                TopList topList = response.body();
                                Feed feed = topList.getFeed();

                                if (feed == null) {
                                    return resultReturn;
                                }

                                List<Entry> shows = feed.getEntry();

                                if (shows == null) {
                                    return resultReturn;
                                }

                                for (int i = 0; i < shows.size(); i++)
                                {
                                    Entry entry = shows.get(i);
                                    String id = entry.getId().getAttributes().getImId();
                                    retrofit2.Response<FeedLookup> showResponse = mService.lookup(id).execute();

                                    if (showResponse.isSuccessful()) {
                                        FeedLookup show = showResponse.body();

                                        SlimSubscription subscription = show.getResults().get(0).toSubscription();
                                        if (subscription != null) {
                                            subscription.setDescription(entry.getSummary().getLabel());
                                            resultReturn.addResult(subscription);
                                        }
                                    }
                                }

                                return resultReturn;
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new RxBasicSubscriber<ISearchResult>() {
                            @Override
                            public void onNext(ISearchResult iSearchResult) {
                                argCallback.result(iSearchResult);
                            }
                        });
            }

            @Override
            public void onFailure(Call<TopList> call, Throwable t) {
                ErrorUtils.handleException(t);
            }
        });
    }

    private String generateUrl(@NonNull String argTerm) {
        return BASE_URL + argTerm + PODCAST_FILTER + EXPLICIT;
    }

    private String generateTerm(@NonNull ISearchParameters argParameters) throws UnsupportedEncodingException {
        String joinedKeywords = TextUtils.join(QUERY_SEPARATOR, argParameters.getKeywords());
        return URLEncoder.encode(joinedKeywords, ENCODING);
    }

    @Override
    protected AsyncTask getAsyncTask() {
        return asyncTask;
    }

    private class QueryITunes extends AsyncTask<String, Void, ISearchResult> {

        private URL mURL;
        private Callback mCallback;
        private String mKeywords;

        public QueryITunes(@NonNull URL argUrl, @NonNull String argKeywords, @NonNull Callback argCallback) {
            mURL = argUrl;
            mCallback = argCallback;
            mKeywords = argKeywords;
        }

        protected ISearchResult doInBackground(String... string) {

            JsonNode node;
            TypeReference<List<ITunesSlimSubscription>> typeRef = new TypeReference<List<ITunesSlimSubscription>>(){};
            List<ITunesSlimSubscription> list = new LinkedList<>();

            try {
                Request request = new Request.Builder().url(mURL).get().build();
                Response response = getOkHttpClient().newCall(request).execute();

                //node = mapper.readTree(mURL);
                node = mapper.readTree(response.body().bytes());
                node = node.get("results");
                list = mapper.readValue(node.traverse(), typeRef);
            } catch (IOException e) {
                e.printStackTrace();
                mCallback.error(e);
            }

            GenericSearchResult result = null;
            try {
                result = new GenericSearchResult(URLDecoder.decode(mKeywords, ENCODING));
            } catch (UnsupportedEncodingException e) {
                ErrorUtils.handleException(e);
                result = new GenericSearchResult(mKeywords.replace("+", " "));
            }

            for (ITunesSlimSubscription itunesSlimSubscription : list) {

                URL url;
                try {
                    url = new URL(itunesSlimSubscription.getUrl());
                } catch (MalformedURLException e) {
                    continue;
                }
                String imageUrl = itunesSlimSubscription.getImageurl();
                String title = itunesSlimSubscription.getTitle();

                SlimSubscription subscription = new SlimSubscription(title, url, imageUrl);


                result.addResult(subscription);
            }

            return result;
        }

        protected void onPostExecute(ISearchResult argResult) {
            if (argResult != null) {
                mCallback.result(argResult);
            }
        }
    }

    private static class ITunesSlimSubscription {
        String title;
        String url;
        String imageurl;


        public ITunesSlimSubscription() {
        }

        public String getTitle() {
            return title;
        }

        @JsonProperty("trackName")
        public void setTitle(String title) {
            this.title = title;
        }

        public String getUrl() {
            return url;
        }

        @JsonProperty("feedUrl")
        public void setUrl(String url) {
            this.url = url;
        }

        public String getImageurl() {
            return imageurl;
        }

        @JsonProperty("artworkUrl600")
        public void setImageurl(String imageurl) {
            this.imageurl = imageurl;
        }
    }
}
