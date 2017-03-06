package net.squanchy.schedule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.squanchy.eventdetails.domain.view.ExperienceLevel;
import net.squanchy.schedule.domain.view.Event;
import net.squanchy.schedule.domain.view.Schedule;
import net.squanchy.schedule.domain.view.SchedulePage;
import net.squanchy.service.firebase.FirebaseDbService;
import net.squanchy.service.firebase.model.FirebaseDays;
import net.squanchy.service.firebase.model.FirebaseEvent;
import net.squanchy.service.firebase.model.FirebaseEvents;
import net.squanchy.service.firebase.model.FirebaseSpeaker;
import net.squanchy.service.firebase.model.FirebaseSpeakers;
import net.squanchy.support.lang.Checksum;
import net.squanchy.support.lang.Func1;
import net.squanchy.support.lang.Func2;
import net.squanchy.support.lang.Lists;
import net.squanchy.support.lang.Optional;

import io.reactivex.Observable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

import static net.squanchy.support.lang.Lists.filter;
import static net.squanchy.support.lang.Lists.find;
import static net.squanchy.support.lang.Lists.map;

class ScheduleService {

    private final FirebaseDbService dbService;
    private final Checksum checksum;

    ScheduleService(FirebaseDbService dbService, Checksum checksum) {
        this.dbService = dbService;
        this.checksum = checksum;
    }

    public Observable<Schedule> schedule() {
        Observable<FirebaseEvents> sessionsObservable = dbService.events();
        Observable<FirebaseSpeakers> speakersObservable = dbService.speakers();
        final Observable<FirebaseDays> daysObservable = dbService.days();

        return Observable.combineLatest(sessionsObservable, speakersObservable, combineSessionsAndSpeakers())
                .map(mapEventsToDays())
                .withLatestFrom(daysObservable, combineSessionsById())
                .subscribeOn(Schedulers.io());
    }

    private BiFunction<FirebaseEvents, FirebaseSpeakers, List<Event>> combineSessionsAndSpeakers() {
        return (apiSchedule, apiSpeakers) -> Lists.map(apiSchedule.events, combineEventWith(apiSpeakers));
    }

    private Func1<FirebaseEvent, Event> combineEventWith(FirebaseSpeakers apiSpeakers) {
        return apiEvent -> {
            List<FirebaseSpeaker> speakers = speakersForEvent(apiEvent, apiSpeakers);

            return Event.create(
                    apiEvent.id,
                    checksum.getChecksumOf(apiEvent.id),
                    apiEvent.day_id,
                    apiEvent.name,
                    apiEvent.place_id,
                    Optional.fromNullable(apiEvent.experience_level).flatMap(ExperienceLevel::fromNullableRawLevel),
                    map(speakers, toSpeakerName()));
        };
    }

    private List<FirebaseSpeaker> speakersForEvent(FirebaseEvent apiEvent, FirebaseSpeakers apiSpeakers) {
        List<Optional<FirebaseSpeaker>> speakers = map(apiEvent.speaker_ids, speakerId -> findSpeaker(apiSpeakers, speakerId));
        List<Optional<FirebaseSpeaker>> presentSpeakers = filter(speakers, Optional::isPresent);
        return map(presentSpeakers, Optional::get);
    }

    private Optional<FirebaseSpeaker> findSpeaker(FirebaseSpeakers apiSpeakers, String speakerId) {
        return find(apiSpeakers.speakers, apiSpeaker -> apiSpeaker.id.equals(speakerId));
    }

    private Func1<FirebaseSpeaker, String> toSpeakerName() {
        return apiSpeaker -> apiSpeaker != null ? apiSpeaker.name : null;
    }

    private Function<List<Event>, HashMap<String, List<Event>>> mapEventsToDays() {
        return events -> Lists.reduce(new HashMap<>(), events, listToDaysHashMap());
    }

    private Func2<HashMap<String, List<Event>>, Event, HashMap<String, List<Event>>> listToDaysHashMap() {
        return (map, event) -> {
            List<Event> dayList = getOrCreateDayList(map, event);
            dayList.add(event);
            map.put(event.dayId(), dayList);
            return map;
        };
    }

    private List<Event> getOrCreateDayList(HashMap<String, List<Event>> map, Event event) {
        List<Event> currentList = map.get(event.dayId());

        if (currentList == null) {
            currentList = new ArrayList<>();
            map.put(event.dayId(), currentList);
        }

        return currentList;
    }

    private BiFunction<HashMap<String, List<Event>>, FirebaseDays, Schedule> combineSessionsById() {
        return (map, apiDays) -> {
            List<SchedulePage> pages = new ArrayList<>(map.size());
            for (String dayId : map.keySet()) {
                Optional<String> date = findDate(apiDays, dayId);
                if (date.isPresent()) {
                    pages.add(SchedulePage.create(date.get(), map.get(dayId)));
                }
            }

            return Schedule.create(pages);
        };
    }

    private Optional<String> findDate(FirebaseDays apiDays, String dayId) {
        return find(apiDays.days, firebaseDay -> firebaseDay.id.equals(String.valueOf(dayId)))
                .map(firebaseDay -> firebaseDay.date);
    }
}
