package com.sfb.systemgroups;

import com.sfb.objects.Unit;

public class ShuttleBay implements Systems {

  private final Unit owningUnit;

  public ShuttleBay(Unit owningUnit) {
    this.owningUnit = owningUnit;

  }

  @Override
  public void init(java.util.Map<String, Object> values) {
    // TODO Auto-generated method stub

  }

  @Override
  public int fetchOriginalTotalBoxes() {
    return 0;
  }

  @Override
  public int fetchRemainingTotalBoxes() {
    return 0;
  }

  @Override
  public void cleanUp() {
    // TODO Auto-generated method stub

  }

  @Override
  public com.sfb.objects.Unit fetchOwningUnit() {
    return this.owningUnit;
  }

}
