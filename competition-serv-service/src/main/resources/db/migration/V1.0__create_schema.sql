drop table if exists competition_properties;

drop table if exists category_restriction;

drop table if exists event;

drop table if exists mat_description;

drop table if exists registration_info;

drop table if exists competition_properties_staff_ids;

drop table if exists promo_code;

drop table if exists competitor;

drop table if exists registration_period;

drop table if exists registration_group;

drop table if exists reg_group_reg_period;

drop table if exists schedule_period;

drop table if exists category_descriptor_restriction;

drop table if exists competitor_categories;

drop table if exists schedule_entry;

drop table if exists stage_descriptor;

drop table if exists fight_description;

drop table if exists comp_score;

drop table if exists fight_start_times;

drop table if exists fight_result_option;

drop table if exists stage_input_descriptor;

drop table if exists registration_group_categories;

drop table if exists competitor_selector;

drop table if exists competitor_selector_selector_value;

drop table if exists competitor_stage_result;

drop index if exists fd_number_on_mat;
drop index if exists fd_number_in_round;
drop index if exists fd_start_time;

create table category_restriction
(
    id        varchar(255) not null
        constraint category_restriction_pkey
            primary key,
    max_value varchar(255),
    min_value varchar(255),
    name      varchar(255),
    type      varchar(255),
    value     varchar(255),
    alias     varchar(255),
    unit      varchar(255)
);

create table competition_properties
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
    status                      varchar(255) not null,
    competition_image           oid,
    competition_info_template   oid
);


create table event
(
    id             varchar(255) not null
        constraint event_pkey
            primary key,
    category_id    varchar(255),
    competition_id varchar(255),
    correlation_id varchar(255),
    mat_id         varchar(255),
    payload        text,
    type           varchar(255)
);


create table registration_info
(
    id                varchar(255) not null
        constraint registration_info_pkey
            primary key
        constraint fkkn1g93oujod8w0b40ojdj16cy
            references competition_properties on delete cascade,
    registration_open boolean      not null
);

create table competition_properties_staff_ids
(
    competition_properties_id varchar(255) not null
        constraint fkryyutsan2yax6j6kts8xdqmwf
            references competition_properties on delete cascade,
    staff_id                  varchar(255) not null,
    constraint competition_properties_staff_ids_pkey
        primary key (competition_properties_id, staff_id)

);

create table promo_code
(
    id             varchar(255) not null
        constraint promo_code_pkey
            primary key,
    coefficient    numeric(19, 2),
    competition_id varchar(255)
        constraint fk93bww360lp5pakmmaciweqpb1
            references competition_properties on delete cascade,
    expire_at      timestamp,
    start_at       timestamp
);

create table competitor
(
    id                  varchar(255) not null
        constraint competitor_pkey
            primary key,
    academy_id          varchar(255),
    academy_name        varchar(255),
    birth_date          timestamp,
    competition_id      varchar(255)
        constraint competitor_competition_id_fkey
            references competition_properties on delete cascade,
    email               varchar(255),
    first_name          varchar(255),
    last_name           varchar(255),
    promo               varchar(255)
        constraint competitor_promo_code_fkey
            references promo_code,
    registration_status varchar(255),
    user_id             varchar(255)
);

create table registration_period
(
    id                   varchar(255) not null
        constraint registration_period_pkey
            primary key,
    end_date             timestamp,
    name                 varchar(255),
    start_date           timestamp,
    registration_info_id varchar(255) not null
        constraint fk45qkmmy3u7pp3chdinw2y5pxh
            references registration_info on delete cascade
);

create table registration_group
(
    id                   varchar(255) not null
        constraint registration_group_pkey
            primary key,
    default_group        boolean      not null,
    display_name         varchar(255),
    registration_fee     numeric(19, 2),
    registration_info_id varchar(255) not null
        constraint fk4e6y1qlhaqfsdk5no3e21uask
            references registration_info on delete cascade
);

create table reg_group_reg_period
(
    reg_group_id  varchar(255) not null
        constraint fkfhwupl1py85g0cyjikvp794hi
            references registration_group on delete cascade,
    reg_period_id varchar(255) not null
        constraint fkn4hr8mvec1fixuba1wmv1271r
            references registration_period on delete cascade,
    constraint reg_group_reg_period_pkey
        primary key (reg_group_id, reg_period_id)
);


create table schedule_period
(
    id                  varchar(255) not null
        constraint schedule_period_pkey
            primary key,
    name                varchar(255),
    is_active           boolean,
    start_time          timestamp,
    risk_percent        numeric(19, 2),
    time_between_fights integer      not null,
    end_time            timestamp,
    competition_id      varchar(255)
        constraint fkeai1ckn6fv7x90xkah9s48faa
            references competition_properties on delete cascade
);

create table mat_description
(
    id        varchar(255) not null
        constraint mat_description_pkey
            primary key,
    name      varchar(255),
    mat_order integer,
    period_id varchar(255) not null
        constraint fkheux8852yfm9g3iwegpqr8sbe
            references schedule_period on delete cascade
);

create table category_descriptor
(
    id                varchar(255) not null
        constraint category_descriptor_pkey
            primary key,
    competition_id    varchar(255),
    name              varchar(255),
    registration_open boolean      not null
);

create table category_descriptor_restriction
(
    category_descriptor_id  varchar(255) not null
        constraint fkk8wjeh0jor53tmgwqf9ksnvh
            references category_descriptor on delete cascade,
    category_restriction_id varchar(255) not null
        constraint fkkrfbh806v4qi47vg3fatmcol0
            references category_restriction on delete cascade,
    restriction_order       integer      not null,
    constraint category_descriptor_restriction_pkey
        primary key (category_descriptor_id, category_restriction_id)
);

create table competitor_categories
(
    competitors_id varchar(255) not null
        constraint fk8mn514i4vj9gafgmqdss7vwks
            references competitor on delete cascade,
    categories_id  varchar(255) not null
        constraint fkmojk2a016rw6htwed62g1oypf
            references category_descriptor on delete cascade,
    constraint competitor_categories_pkey
        primary key (competitors_id, categories_id)
);

create table stage_descriptor
(
    id                      varchar(255)   not null
        constraint stage_descriptor_pkey
            primary key,
    category_id             varchar(255)
        constraint stage_descriptor_category_id_fkey
            references category_descriptor on delete cascade,
    bracket_type            varchar(255),
    competition_id          varchar(255),
    name                    varchar(255),
    stage_order             integer,
    stage_status            varchar(255),
    stage_type              varchar(255),
    wait_for_previous       boolean,
    has_third_place_fight   boolean,
    force_manual_assignment boolean,
    output_size             integer,
    fight_duration          numeric(19, 2) not null
);

create table additional_group_sorting_descriptor
(
    stage_id             varchar(255) not null
        constraint additional_group_sorting_descriptor_stage_id_fkey references stage_descriptor on delete cascade,
    group_sort_direction varchar(255),
    group_sort_specifier varchar(255) not null,
    constraint additional_group_sorting_descriptor_pkey
        primary key (stage_id, group_sort_specifier)
);

create table group_descriptor
(
    id       varchar(255) not null
        constraint group_descriptor_pkey
            primary key,
    stage_id varchar(255)
        constraint group_descriptor_stage_descriptor_id_fkey
            references stage_descriptor on delete cascade,
    name     varchar(255),
    size     integer      not null
);

create table fight_result_option
(
    id                       varchar(255)   not null,
    winner_additional_points numeric(19, 2),
    loser_additional_points  numeric(19, 2),
    description              varchar(255),
    short_name               varchar(255)   not null,
    draw                     boolean,
    winner_points            numeric(19, 2) not null,
    loser_points             numeric(19, 2) not null,
    stage_id                 varchar(255)
        constraint fkhj7y0idxgjgbg8b4el2qr8nc6
            references stage_descriptor on delete cascade,
    constraint fight_result_option_pkey
        primary key (id, stage_id)
);

create table schedule_entry
(
    id             varchar(255) not null
        constraint schedule_entry_pkey
            primary key,
    period_id      varchar(255) not null
        constraint fkafy2hinwcl6a1ugke2uctic0w
            references schedule_period on delete cascade,
    duration       numeric(19, 2),
    entry_type     varchar(255) not null,
    start_time     timestamp    not null,
    end_time       timestamp,
    schedule_order integer      not null,
    description    varchar(255),
    name           varchar(255),
    color          varchar(255),
    constraint schedule_unique_period_mat_order
        unique (period_id, schedule_order, entry_type)
);

create table schedule_requirement
(
    id               varchar(255) not null
        constraint schedule_requirement_pkey
            primary key,
    period_id        varchar(255) not null
        constraint schedule_requirement_period_id_fkey
            references schedule_period on delete cascade,
    mat_id           varchar(255)
        constraint schedule_requirement_mat_id_fkey
            references mat_description,
    entry_type       varchar(255) not null,
    entry_order      integer      not null,
    start_time       timestamp,
    force            boolean,
    end_time         timestamp,
    duration_minutes numeric(19, 2),
    description      varchar(255),
    name             varchar(255),
    color            varchar(255),
    constraint schedule_requirement_unique_period_mat_order
        unique (period_id, mat_id, entry_order)
);

create table fight_description
(
    id                varchar(255) not null
        constraint fight_description_pkey
            primary key,
    category_id       varchar(255)
        constraint fight_description_category_id_fkey
            references category_descriptor on delete cascade,
    competition_id    varchar(255)
        constraint fight_description_competition_id_fkey
            references competition_properties on delete cascade,
    duration          numeric(19, 2),
    fight_name        varchar(255),
    winner_id         varchar(255)
        constraint fight_description_winner_id_fkey
            references competitor,
    reason            varchar(255),
    result_type       varchar(255),
    lose_fight        varchar(255),
    mat_id            varchar(255)
        constraint fight_description_mat_description_fkey
            references mat_description,
    number_in_round   integer      not null,
    number_on_mat     integer,
    period            varchar(255)
        constraint fight_description_schedule_period
            references schedule_period,
    priority          integer      not null,
    round             integer,
    round_type        varchar(255),
    status            varchar(255) not null,
    start_time        timestamp,
    win_fight         varchar(255),
    stage_id          varchar(255)
        constraint fk83j4njug11q161thma55h5b6a
            references stage_descriptor on delete cascade,
    group_id          varchar(255)
        constraint fight_description_group_descriptor_fkey
            references group_descriptor on delete cascade,
    invalid           boolean,
    schedule_entry_id varchar(255)
        constraint fight_description_schedule_entry_fkey
            references schedule_entry,
    constraint fight_description_fight_output_fkey foreign key (result_type, stage_id)
        references fight_result_option
);

CREATE INDEX fd_number_on_mat ON fight_description (number_on_mat);
CREATE INDEX fd_number_in_round ON fight_description (number_in_round);
CREATE INDEX fd_start_time ON fight_description (start_time);


create table schedule_requirement_fight_description
(
    requirement_id varchar(255) not null
        constraint schedule_requirement_fight_description_requirement_id references schedule_requirement on delete cascade,
    fight_id       varchar(255) not null
        constraint schedule_requirement_fight_description_fight_id references fight_description on delete cascade,
    constraint schedule_requirement_fight_description_pkey
        primary key (requirement_id, fight_id)
);

create table schedule_requirement_category_description
(
    requirement_id varchar(255) not null
        constraint schedule_requirement_category_description_requirement_id references schedule_requirement on delete cascade,
    category_id    varchar(255) not null
        constraint schedule_requirement_category_description_category_id references category_descriptor on delete cascade,
    constraint schedule_requirement_category_description_pkey
        primary key (requirement_id, category_id)
);



create table category_schedule_entry
(
    category_id       varchar(255) not null
        constraint category_schedule_entry_category_fkey references category_descriptor on delete cascade,
    schedule_entry_id varchar(255) not null
        constraint category_schedule_entry_schedule_entry_fkey references schedule_entry on delete cascade,
    constraint category_schedule_entry_pkey primary key (category_id, schedule_entry_id)
);

create table schedule_entry_schedule_requirement
(
    schedule_requirement_id varchar(255) not null
        constraint schedule_entry_schedule_requirement_schedule_requirement_fkey references schedule_requirement on delete cascade,
    schedule_entry_id       varchar(255) not null
        constraint schedule_entry_schedule_requirement_schedule_entry_fkey references schedule_entry on delete cascade,
    constraint schedule_entry_schedule_requirement_pkey primary key (schedule_requirement_id, schedule_entry_id)
);



create table comp_score
(
    advantages                     integer,
    penalties                      integer,
    points                         integer,
    placeholder_id                 varchar(255),
    parent_fight_id                varchar(255)
        constraint compscore_parent_fight_fk
            references fight_description on delete cascade,
    parent_reference_type          varchar(255),
    compscore_competitor_id        varchar(255)
        constraint fkiuy2929idw7lx296op7w8govx
            references competitor on delete cascade,
    compscore_fight_description_id varchar(255) not null
        constraint fk5jdsq3wdtfltdvjb7hmmqu497
            references fight_description on delete cascade,
    comp_score_order               integer,
    constraint comp_score_pkey
        primary key (comp_score_order, compscore_fight_description_id)
);

create table stage_input_descriptor
(
    id                    varchar(255) not null
        constraint stage_input_descriptor_pkey
            primary key
        constraint stage_input_fk_stage_descriptor
            references stage_descriptor on delete cascade,
    distribution_type     varchar(255),
    number_of_competitors integer      not null
);

create table registration_group_categories
(
    registration_group_id varchar(255) not null
        constraint fklcnvc1xtt6gfrwixoj0mvqchg
            references registration_group on delete cascade,
    category_id           varchar(255) not null
        constraint reg_group_category_id_fkey
            references category_descriptor on delete cascade,
    constraint registration_group_categories_pkey
        primary key (registration_group_id, category_id)
);

create table competitor_selector
(
    id                varchar(255) not null
        constraint competitor_selector_pkey
            primary key,
    apply_to_stage_id varchar(255),
    classifier        varchar(255),
    logical_operator  varchar(255),
    operator          varchar(255),
    stage_input_id    varchar(255)
        constraint fkhn69xal0k08it5niovv40cr6l
            references stage_input_descriptor on delete cascade
);

create table competitor_selector_selector_value
(
    competitor_selector_id varchar(255) not null
        constraint fk1s0fmnd8kgdabvp3qml62yee2
            references competitor_selector on delete cascade,
    selector_value         varchar(255) not null,
    constraint competitor_selector_selector_value_pkey
        primary key (competitor_selector_id, selector_value)
);

create table competitor_stage_result
(
    group_id      varchar(255),
    conflicting   boolean,
    place         integer      not null,
    points        numeric(19, 2),
    round_type    varchar(255),
    round         integer,
    stage_id      varchar(255) not null
        constraint competitor_stage_result
            references stage_descriptor on delete cascade,
    competitor_id varchar(255) not null
        constraint fk9axdhjm23nfsx1xuofkmq5cyo
            references competitor on delete cascade,
    constraint competitor_stage_result_pkey
        primary key (stage_id, competitor_id)
);
