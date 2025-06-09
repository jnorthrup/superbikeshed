# devtools::source_url("https://raw.githubusercontent.com/holgerbrandl/datautils/v1.46/R/core_commons.R")

devtools::source_url("https://raw.githubusercontent.com/holgerbrandl/datautils/v1.46/R/stats/ci_commons.R")


benchData = list.files(pattern = "scriptlet_runtimes*") %>% map_df(~ {
    # .x= "scriptlet_runtimes_pr93.txt"
    read_tsv(.x) %>%
        mutate(version = str_match(.x, "scriptlet_runtimes_(.*).txt") %>% get_col(2)) %>%
        mutate_at(vars(cached), as.logical)
})

benchData %>%
    filter(cached == FALSE) %>%
    group_by(version, script_name) %>%
    plot_ci(runtime_ms) +
    ggtitle("without cached jar") +
    scale_y_continuous(limits = c(0, NA))

benchData %>%
    filter(cached == TRUE) %>%
    group_by(version, script_name) %>%
    plot_ci(runtime_ms) +
    ggtitle("with cached jar") +
    scale_y_continuous(limits = c(0, NA))


benchData %>% ggplot(aes(version, runtime_ms, color = cached)) +
    geom_jitter(height = 0, width = 0.1) +
    scale_y_continuous(limits = c(0, NA)) +
    facet_grid(~ script_name)

