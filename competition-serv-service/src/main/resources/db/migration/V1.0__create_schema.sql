CREATE SCHEMA IF NOT EXISTS compservice;

drop table if exists compservice.competition_properties cascade;

drop table if exists compservice.category_restriction cascade;

drop table if exists compservice.dashboard_period cascade;

drop table if exists compservice.event cascade;

drop table if exists compservice.mat_description cascade;

drop table if exists compservice.registration_info cascade;

drop table if exists compservice.competition_properties_staff_ids cascade;

drop table if exists compservice.promo_code cascade;

drop table if exists compservice.competitor cascade;

drop table if exists compservice.registration_period cascade;

drop table if exists compservice.registration_group cascade;

drop table if exists compservice.reg_group_reg_period cascade;

drop table if exists compservice.schedule_period cascade;

drop table if exists compservice.mat_schedule_container cascade;

drop table if exists compservice.schedule_period_properties cascade;

drop table if exists compservice.category_descriptor_restriction cascade;

drop table if exists compservice.competitor_categories cascade;

drop table if exists compservice.schedule_entries cascade;

drop table if exists compservice.stage_descriptor cascade;

drop table if exists compservice.fight_description cascade;

drop table if exists compservice.comp_score cascade;

drop table if exists compservice.fight_start_times cascade;

drop table if exists compservice.points_assignment_descriptor cascade;

drop table if exists compservice.stage_input_descriptor cascade;

drop table if exists compservice.registration_group_categories cascade;

drop table if exists compservice.competitor_selector cascade;

drop table if exists compservice.competitor_selector_selector_value cascade;

drop table if exists compservice.competitor_stage_result cascade;

create table compservice.category_restriction
(
    id        varchar(255) not null
        constraint category_restriction_pkey
            primary key,
    max_value varchar(255),
    min_value varchar(255),
    name      varchar(255),
    type      integer,
    unit      varchar(255)
);

create table compservice.competition_properties
(
    id                          varchar(255) not null
        constraint competition_properties_pkey
            primary key,
    brackets_published          boolean      not null,
    competition_name            varchar(255) not null,
    creation_timestamp          bigint       not null,
    creator_id                  varchar(255) not null,
    email_notifications_enabled boolean,
    email_template              varchar(255),
    end_date                    timestamp    not null,
    schedule_published          boolean      not null,
    start_date                  timestamp    not null,
    time_zone                   varchar(255) not null,
    status                      integer      not null,
    competition_image           oid,
    competition_info_template   oid
);

create table compservice.dashboard_period
(
    id             varchar(255) not null
        constraint dashboard_period_pkey
            primary key,
    competition_id varchar(255) not null
        constraint dashboard_competition_id_fkey
            references compservice.competition_properties on delete cascade,
    is_active      boolean      not null,
    name           varchar(255),
    start_time     timestamp
);

create table compservice.event
(
    id             varchar(255) not null
        constraint event_pkey
            primary key,
    category_id    varchar(255),
    competition_id varchar(255),
    correlation_id varchar(255),
    mat_id         varchar(255),
    payload        text,
    type           integer
);

create table compservice.mat_description
(
    id                  varchar(255) not null
        constraint mat_description_pkey
            primary key,
    name                varchar(255),
    dashboard_period_id varchar(255) not null
        constraint fk3rv512vhq8j2k4c40g3ly5792
            references compservice.dashboard_period on update cascade,
    mats_order          integer
);

create table compservice.registration_info
(
    id                varchar(255) not null
        constraint registration_info_pkey
            primary key
        constraint fkkn1g93oujod8w0b40ojdj16cy
            references compservice.competition_properties on delete cascade,
    registration_open boolean      not null
);

create table compservice.competition_properties_staff_ids
(
    competition_properties_id varchar(255) not null
        constraint fkryyutsan2yax6j6kts8xdqmwf
            references compservice.competition_properties on delete cascade,
    staff_id                  varchar(255) not null,
    constraint competition_properties_staff_ids_pkey
        primary key (competition_properties_id, staff_id)

);

create table compservice.promo_code
(
    id             varchar(255) not null
        constraint promo_code_pkey
            primary key,
    coefficient    numeric(19, 2),
    competition_id varchar(255)
        constraint fk93bww360lp5pakmmaciweqpb1
            references compservice.competition_properties on delete cascade,
    expire_at      timestamp,
    start_at       timestamp
);

create table compservice.competitor
(
    id                  varchar(255) not null
        constraint competitor_pkey
            primary key,
    academy_id          varchar(255),
    academy_name        varchar(255),
    birth_date          timestamp,
    competition_id      varchar(255)
        constraint competitor_competition_id_fkey
            references compservice.competition_properties on delete cascade,
    email               varchar(255),
    first_name          varchar(255),
    last_name           varchar(255),
    promo               varchar(255)
        constraint competitor_promo_code_fkey
            references compservice.promo_code,
    registration_status integer,
    user_id             varchar(255)
);

create table compservice.registration_period
(
    id                   varchar(255) not null
        constraint registration_period_pkey
            primary key,
    end_date             timestamp,
    name                 varchar(255),
    start_date           timestamp,
    registration_info_id varchar(255) not null
        constraint fk45qkmmy3u7pp3chdinw2y5pxh
            references compservice.registration_info on delete cascade
);

create table compservice.registration_group
(
    id                   varchar(255) not null
        constraint registration_group_pkey
            primary key,
    default_group        boolean      not null,
    display_name         varchar(255),
    registration_fee     numeric(19, 2),
    registration_info_id varchar(255) not null
        constraint fk4e6y1qlhaqfsdk5no3e21uask
            references compservice.registration_info on delete cascade
);

create table compservice.reg_group_reg_period
(
    reg_group_id  varchar(255) not null
        constraint fkfhwupl1py85g0cyjikvp794hi
            references compservice.registration_period on delete cascade,
    reg_period_id varchar(255) not null
        constraint fkn4hr8mvec1fixuba1wmv1271r
            references compservice.registration_group on delete cascade,
    constraint reg_group_reg_period_pkey
        primary key (reg_group_id, reg_period_id)
);

create table compservice.schedule_period
(
    id             varchar(255) not null
        constraint schedule_period_pkey
            primary key,
    name           varchar(255),
    number_of_mats integer      not null,
    start_time     timestamp,
    competition_id varchar(255)
        constraint fkeai1ckn6fv7x90xkah9s48faa
            references compservice.competition_properties on delete cascade
);

create table compservice.mat_schedule_container
(
    id           varchar(255) not null
        constraint mat_schedule_container_pkey
            primary key,
    total_fights integer      not null,
    period_id    varchar(255) not null
        constraint fkheux8852yfm9g3iwegpqr8sbe
            references compservice.schedule_period on delete cascade
);

create table compservice.schedule_period_properties
(
    id                  varchar(255) not null
        constraint schedule_period_properties_pkey
            primary key,
    name                varchar(255),
    number_of_mats      integer      not null,
    risk_percent        numeric(19, 2),
    start_time          timestamp,
    time_between_fights integer      not null,
    competition_id      varchar(255)
        constraint fkotxnpcqbjgdxl6c6e0ndigdku
            references compservice.competition_properties on delete cascade
);

create table compservice.category_descriptor
(
    id                   varchar(255) not null
        constraint category_descriptor_pkey
            primary key,
    competition_id       varchar(255),
    fight_duration       numeric(19, 2),
    name                 varchar(255),
    registration_open    boolean      not null,
    period_properties_id varchar(255)
        constraint fkmpbsg54mghn26gyse486xwwry
            references compservice.schedule_period_properties,
    schedule_period_id   varchar(255)
        constraint fkkg7upfo8tok049xmxratl9umi
            references compservice.schedule_period
);

create table compservice.category_descriptor_restriction
(
    category_descriptor_id  varchar(255) not null
        constraint fkk8wjeh0jor53tmgwqf9ksnvh
            references compservice.category_descriptor on delete cascade,
    category_restriction_id varchar(255) not null
        constraint fkkrfbh806v4qi47vg3fatmcol0
            references compservice.category_restriction on delete cascade,
    constraint category_descriptor_restriction_pkey
        primary key (category_descriptor_id, category_restriction_id)
);

create table compservice.competitor_categories
(
    competitors_id varchar(255) not null
        constraint fk8mn514i4vj9gafgmqdss7vwks
            references compservice.competitor on delete cascade,
    categories_id  varchar(255) not null
        constraint fkmojk2a016rw6htwed62g1oypf
            references compservice.category_descriptor on delete cascade,
    constraint competitor_categories_pkey
        primary key (competitors_id, categories_id)
);

create table compservice.schedule_entries
(
    period_id        varchar(255) not null
        constraint fkafy2hinwcl6a1ugke2uctic0w
            references compservice.schedule_period on delete cascade,
    category_id      varchar(255) not null
        constraint schedule_entries_category_id_fkey
            references compservice.category_descriptor on delete cascade,
    fight_duration   numeric(19, 2),
    number_of_fights integer      not null,
    start_time       timestamp,
    schedule_order   integer      not null,
    constraint schedule_entries_pkey
        primary key (period_id, category_id, schedule_order)
);

create table compservice.stage_descriptor
(
    id                    varchar(255) not null
        constraint stage_descriptor_pkey
            primary key,
    category_id           varchar(255)
        constraint stage_descriptor_category_id_fkey
            references compservice.category_descriptor on delete cascade,
    bracket_type          integer,
    competition_id        varchar(255),
    name                  varchar(255),
    stage_order           integer,
    stage_status          integer,
    stage_type            integer,
    wait_for_previous     boolean,
    has_third_place_fight boolean
);

create table compservice.group_descriptor
(
    id       varchar(255) not null
        constraint group_descriptor_pkey
            primary key,
    stage_id varchar(255)
        constraint group_descriptor_stage_descriptor_id_fkey
            references compservice.stage_descriptor on delete cascade,
    name     varchar(255),
    size     integer not null
);

create table compservice.fight_description
(
    id                      varchar(255) not null
        constraint fight_description_pkey
            primary key,
    category_id             varchar(255)
        constraint fight_description_category_id_fkey
            references compservice.category_descriptor on delete cascade,
    competition_id          varchar(255)
        constraint fight_description_competition_id_fkey
            references compservice.competition_properties on delete cascade,
    duration                numeric(19, 2),
    fight_name              varchar(255),
    winner_id               varchar(255),
    reason                  varchar(255),
    result_type             integer,
    lose_fight              varchar(255),
    mat_id                  varchar(255),
    number_in_round         integer      not null,
    number_on_mat           integer,
    parent_1_fight_id       varchar(255),
    parent_1_reference_type integer,
    parent_2_fight_id       varchar(255),
    parent_2_reference_type integer,
    period                  varchar(255)
        constraint fight_description_schedule_period
            references compservice.schedule_period,
    priority                integer      not null,
    round                   integer,
    round_type              integer,
    status                  integer,
    start_time              timestamp,
    win_fight               varchar(255),
    stage_id                varchar(255)
        constraint fk83j4njug11q161thma55h5b6a
            references compservice.stage_descriptor on delete cascade,
    group_id                varchar(255)
        constraint fight_description_group_descriptor_fkey
            references compservice.group_descriptor on delete cascade,
    fight_order             integer
);

create table compservice.comp_score
(
    advantages                     integer,
    penalties                      integer,
    points                         integer,
    compscore_competitor_id        varchar(255) not null
        constraint fkiuy2929idw7lx296op7w8govx
            references compservice.competitor on delete cascade,
    compscore_fight_description_id varchar(255) not null
        constraint fk5jdsq3wdtfltdvjb7hmmqu497
            references compservice.fight_description on delete cascade,
    comp_score_order               integer,
    constraint comp_score_pkey
        primary key (compscore_competitor_id, compscore_fight_description_id)
);

create table compservice.fight_start_times
(
    mat_schedule_id varchar(255) not null
        constraint fk3qwq4ltq5uauo58p5e8ko6pld
            references compservice.mat_schedule_container on delete cascade,
    fight_id        varchar(255) not null
        constraint fki3ci3hkg4wfrh8s2xwiellgbv
            references compservice.fight_description on delete cascade,
    fight_number    integer      not null,
    start_time      timestamp,
    fights_order    integer      not null,
    constraint fight_start_times_pkey
        primary key (mat_schedule_id, fights_order)
);

create table compservice.points_assignment_descriptor
(
    id                varchar(255) not null
        constraint points_assignment_descriptor_pkey
            primary key,
    additional_points numeric(19, 2),
    classifier        integer not null,
    points            numeric(19, 2) not null,
    stage_id          varchar(255)
        constraint fkhj7y0idxgjgbg8b4el2qr8nc6
            references compservice.stage_descriptor on delete cascade
);

create table compservice.stage_input_descriptor
(
    id                    varchar(255) not null
        constraint stage_input_descriptor_pkey
            primary key
        constraint stage_input_fk_stage_descriptor
            references compservice.stage_descriptor on delete cascade,
    distribution_type     integer,
    number_of_competitors integer      not null
);

create table compservice.registration_group_categories
(
    registration_group_id varchar(255) not null
        constraint fklcnvc1xtt6gfrwixoj0mvqchg
            references compservice.registration_group on delete cascade,
    category_id           varchar(255) not null
        constraint reg_group_category_id_fkey
            references compservice.category_descriptor on delete cascade,
    constraint registration_group_categories_pkey
        primary key (registration_group_id, category_id)
);

create table compservice.competitor_selector
(
    id                varchar(255) not null
        constraint competitor_selector_pkey
            primary key,
    apply_to_stage_id varchar(255),
    classifier        integer,
    logical_operator  integer,
    operator          integer,
    stage_input_id    varchar(255)
        constraint fkhn69xal0k08it5niovv40cr6l
            references compservice.stage_input_descriptor on delete cascade
);

create table compservice.competitor_selector_selector_value
(
    competitor_selector_id varchar(255) not null
        constraint fk1s0fmnd8kgdabvp3qml62yee2
            references compservice.competitor_selector on delete cascade,
    selector_value         varchar(255) not null,
    constraint competitor_selector_selector_value_pkey
        primary key (competitor_selector_id, selector_value)
);

create table compservice.competitor_stage_result
(
    group_id      varchar(255),
    place         integer,
    points        integer,
    round         integer,
    stage_id      varchar(255) not null
        constraint competitor_stage_result
            references compservice.stage_descriptor on delete cascade,
    competitor_id varchar(255) not null
        constraint fk9axdhjm23nfsx1xuofkmq5cyo
            references compservice.competitor on delete cascade,
    constraint competitor_stage_result_pkey
        primary key (stage_id, competitor_id)
);
