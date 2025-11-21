package app.js.api;

import app.js.service.AnalyticsService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

  private final AnalyticsService analyticsService;

  public AnalyticsController(AnalyticsService analyticsService) {
    this.analyticsService = analyticsService;
  }

  @GetMapping("/by-wiki")
  public Map<String, Long> getEventsByWiki(@RequestParam(defaultValue = "24") int hours) {
    return analyticsService.getEventsByWiki(hours);
  }

  @GetMapping("/by-type")
  public Map<String, Long> getEventsByType(@RequestParam(defaultValue = "24") int hours) {
    return analyticsService.getEventsByType(hours);
  }

  @GetMapping("/top-users")
  public List<Map<String, Object>> getTopUsers(
      @RequestParam(defaultValue = "24") int hours, @RequestParam(defaultValue = "10") int limit) {
    return analyticsService.getTopUsers(hours, limit);
  }

  @GetMapping("/top-pages")
  public List<Map<String, Object>> getTopPages(
      @RequestParam(defaultValue = "24") int hours, @RequestParam(defaultValue = "10") int limit) {
    return analyticsService.getTopPages(hours, limit);
  }

  @GetMapping("/hourly")
  public Map<String, Long> getHourlyDistribution(@RequestParam(defaultValue = "24") int hours) {
    return analyticsService.getHourlyDistribution(hours);
  }

  @GetMapping("/breakdown")
  public Map<String, Object> getEventBreakdown(@RequestParam(defaultValue = "24") int hours) {
    return analyticsService.getEventBreakdown(hours);
  }
}
